package co.edu.unbosque.marketplace.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.dto.DisponibilidadDTO;
import co.edu.unbosque.marketplace.dto.PeriodoDisponibleDTO;
import co.edu.unbosque.marketplace.exception.AccesoDenegadoException;
import co.edu.unbosque.marketplace.exception.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.exception.ReglaNegocioException;
import co.edu.unbosque.marketplace.exception.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.model.Alojamiento;
import co.edu.unbosque.marketplace.model.Alojamiento.EstadoAlojamiento;
import co.edu.unbosque.marketplace.model.PeriodoDisponible;
import co.edu.unbosque.marketplace.model.PeriodoReservado;
import co.edu.unbosque.marketplace.model.RangoFechas;
import co.edu.unbosque.marketplace.model.Usuario;
import co.edu.unbosque.marketplace.model.Usuario.Rol;
import co.edu.unbosque.marketplace.repository.AlojamientoRepository;
import co.edu.unbosque.marketplace.repository.PeriodoDisponibleRepository;
import co.edu.unbosque.marketplace.repository.PeriodoReservadoRepository;

/**
 * Periodos disponibles y consulta de disponibilidad (MA-58, MA-59).
 * <p>
 * Un alojamiento está disponible en [llegada, salida) si está ACTIVA, algún periodo disponible
 * contiene las fechas y ninguna reserva vigente se cruza con ellas. Las reservas vigentes son
 * las confirmadas, en revisión o retenidas mientras se paga sin haber vencido.
 */
@Service
public class DisponibilidadService {

	private static final Logger log = LoggerFactory.getLogger(DisponibilidadService.class);

	private final AlojamientoRepository alojamientoRepository;
	private final PeriodoDisponibleRepository periodoRepository;
	private final PeriodoReservadoRepository reservadoRepository;
	private final Clock reloj;
	private final ZoneId zonaHoraria;

	public DisponibilidadService(AlojamientoRepository alojamientoRepository,
			PeriodoDisponibleRepository periodoRepository, PeriodoReservadoRepository reservadoRepository,
			Clock reloj, @Value("${marketplace.zona-horaria}") ZoneId zonaHoraria) {
		this.alojamientoRepository = alojamientoRepository;
		this.periodoRepository = periodoRepository;
		this.reservadoRepository = reservadoRepository;
		this.reloj = reloj;
		this.zonaHoraria = zonaHoraria;
	}

	// ─────────────────────────────────────────
	// Periodos disponibles — MA-58
	// ─────────────────────────────────────────

	/** Los periodos los ve quien puede ver el alojamiento. */
	@Transactional(readOnly = true)
	public List<PeriodoDisponibleDTO> listarPeriodos(Usuario actor, Long alojamientoId) {
		verificarPuedeVer(actor, buscarAlojamiento(alojamientoId));
		return periodoRepository.findByAlojamientoIdOrderByFechaInicioAsc(alojamientoId).stream()
				.map(PeriodoDisponibleDTO::desde)
				.toList();
	}

	@Transactional
	public PeriodoDisponibleDTO crearPeriodo(Usuario anfitrion, Long alojamientoId, PeriodoDisponibleDTO datos) {
		Alojamiento alojamiento = buscarDelDueno(anfitrion, alojamientoId);
		RangoFechas rango = validarPeriodo(datos);
		if (rango.llegada().isBefore(hoy())) {
			throw fechasInvalidas("El periodo no puede empezar antes de hoy.");
		}
		verificarSinCruces(alojamientoId, -1L, rango);
		PeriodoDisponible periodo = guardar(new PeriodoDisponible(alojamiento, rango));
		log.info("Periodo disponible creado (alojamiento {}, periodo {})", alojamientoId, periodo.getId());
		return PeriodoDisponibleDTO.desde(periodo);
	}

	/**
	 * Cambia las fechas. No se puede reducir dejando por fuera reservas vigentes
	 * (MA-58, escenario 2).
	 */
	@Transactional
	public PeriodoDisponibleDTO editarPeriodo(Usuario anfitrion, Long alojamientoId, Long periodoId,
			PeriodoDisponibleDTO datos) {
		buscarDelDueno(anfitrion, alojamientoId);
		PeriodoDisponible periodo = buscarPeriodo(alojamientoId, periodoId);
		RangoFechas nuevo = validarPeriodo(datos);
		if (!nuevo.salida().isAfter(hoy())) {
			throw fechasInvalidas("El periodo debe terminar después de hoy.");
		}
		verificarSinCruces(alojamientoId, periodoId, nuevo);
		boolean dejaReservasPorFuera = reservasVigentesEn(alojamientoId, periodo.rango()).stream()
				.anyMatch(reservado -> !nuevo.contiene(reservado.rango()));
		if (dejaReservasPorFuera) {
			throw conReservas("No puedes reducir el periodo: dejaría por fuera reservas existentes.");
		}
		periodo.cambiarFechas(nuevo);
		guardar(periodo);
		return PeriodoDisponibleDTO.desde(periodo);
	}

	/** No se puede eliminar si tiene reservas vigentes (MA-58, escenario 2). */
	@Transactional
	public void eliminarPeriodo(Usuario anfitrion, Long alojamientoId, Long periodoId) {
		buscarDelDueno(anfitrion, alojamientoId);
		PeriodoDisponible periodo = buscarPeriodo(alojamientoId, periodoId);
		if (!reservasVigentesEn(alojamientoId, periodo.rango()).isEmpty()) {
			throw conReservas("No puedes eliminar el periodo: tiene reservas.");
		}
		periodoRepository.delete(periodo);
		log.info("Periodo disponible eliminado (alojamiento {}, periodo {})", alojamientoId, periodoId);
	}

	// ─────────────────────────────────────────
	// Consulta — MA-59
	// ─────────────────────────────────────────

	/**
	 * Indica si se puede reservar en [llegada, salida). La llegada no puede ser anterior a hoy y
	 * la salida debe ser posterior a la llegada (escenario 1). {@code actor} puede ser nulo: la
	 * consulta es pública para los alojamientos ACTIVA.
	 */
	@Transactional(readOnly = true)
	public DisponibilidadDTO consultar(Usuario actor, Long alojamientoId, LocalDate llegada, LocalDate salida) {
		if (llegada == null || salida == null) {
			throw fechasInvalidas("Indica la fecha de llegada y la de salida.");
		}
		if (llegada.isBefore(hoy())) {
			throw fechasInvalidas("La llegada no puede ser anterior a hoy.");
		}
		if (!salida.isAfter(llegada)) {
			throw fechasInvalidas("La salida debe ser posterior a la llegada.");
		}
		RangoFechas rango = new RangoFechas(llegada, salida);
		Alojamiento alojamiento = buscarAlojamiento(alojamientoId);
		verificarPuedeVer(actor, alojamiento);

		String motivo = null;
		if (alojamiento.getEstado() != EstadoAlojamiento.ACTIVA) {
			motivo = "NO_ACTIVO";
		} else if (!periodoRepository.algunoContiene(alojamientoId, llegada, salida)) {
			motivo = "FUERA_DE_PERIODO";
		} else if (!reservasVigentesEn(alojamientoId, rango).isEmpty()) {
			motivo = "OCUPADO";
		}
		return new DisponibilidadDTO(alojamientoId, llegada, salida, rango.noches(), motivo == null, motivo);
	}

	// ─────────────────────────────────────────
	// Auxiliares
	// ─────────────────────────────────────────

	private List<PeriodoReservado> reservasVigentesEn(Long alojamientoId, RangoFechas rango) {
		Instant ahora = reloj.instant();
		return reservadoRepository.buscarVigentesQueSeCruzan(alojamientoId, rango.llegada(), rango.salida(), ahora);
	}

	private RangoFechas validarPeriodo(PeriodoDisponibleDTO datos) {
		if (!datos.fechaFin().isAfter(datos.fechaInicio())) {
			throw fechasInvalidas("La fecha de fin debe ser posterior a la de inicio.");
		}
		return new RangoFechas(datos.fechaInicio(), datos.fechaFin());
	}

	private void verificarSinCruces(Long alojamientoId, Long excluirId, RangoFechas rango) {
		if (periodoRepository.existeOtroQueSeCruza(alojamientoId, excluirId, rango.llegada(), rango.salida())) {
			throw periodoSuperpuesto();
		}
	}

	/** La restricción de exclusión de la base de datos atrapa los cruces simultáneos. */
	private PeriodoDisponible guardar(PeriodoDisponible periodo) {
		try {
			return periodoRepository.saveAndFlush(periodo);
		} catch (DataIntegrityViolationException e) {
			throw periodoSuperpuesto();
		}
	}

	private LocalDate hoy() {
		return LocalDate.ofInstant(reloj.instant(), zonaHoraria);
	}

	private Alojamiento buscarAlojamiento(Long alojamientoId) {
		return alojamientoRepository.findById(alojamientoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("El alojamiento no existe."));
	}

	private Alojamiento buscarDelDueno(Usuario anfitrion, Long alojamientoId) {
		Alojamiento alojamiento = buscarAlojamiento(alojamientoId);
		if (!alojamiento.esDe(anfitrion.getId())) {
			throw new AccesoDenegadoException("Solo el anfitrión dueño puede gestionar la disponibilidad.");
		}
		return alojamiento;
	}

	private PeriodoDisponible buscarPeriodo(Long alojamientoId, Long periodoId) {
		return periodoRepository.findById(periodoId)
				.filter(periodo -> periodo.getAlojamiento().getId().equals(alojamientoId))
				.orElseThrow(() -> new RecursoNoEncontradoException("El periodo no existe."));
	}

	/** Igual que el detalle: los no ACTIVA solo los ven el dueño y el administrador. */
	private static void verificarPuedeVer(Usuario actor, Alojamiento alojamiento) {
		boolean puedeVerlo = alojamiento.getEstado() == EstadoAlojamiento.ACTIVA
				|| (actor != null && (actor.getRol() == Rol.ADMINISTRADOR || alojamiento.esDe(actor.getId())));
		if (!puedeVerlo) {
			throw new RecursoNoEncontradoException("El alojamiento no existe.");
		}
	}

	private static SolicitudInvalidaException fechasInvalidas(String mensaje) {
		return new SolicitudInvalidaException("FECHAS_INVALIDAS", mensaje);
	}

	private static ReglaNegocioException periodoSuperpuesto() {
		return new ReglaNegocioException("PERIODO_SUPERPUESTO", "El periodo se cruza con otro periodo del alojamiento.");
	}

	private static ReglaNegocioException conReservas(String mensaje) {
		return new ReglaNegocioException("PERIODO_CON_RESERVAS", mensaje);
	}
}
