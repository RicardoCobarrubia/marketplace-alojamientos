package co.edu.unbosque.marketplace.reservas.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.comun.excepcion.AccesoDenegadoException;
import co.edu.unbosque.marketplace.comun.excepcion.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.disponibilidad.infraestructura.PeriodoReservadoRepository;
import co.edu.unbosque.marketplace.reservas.dominio.CalculadoraDevolucion;
import co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;
import co.edu.unbosque.marketplace.reservas.infraestructura.FiltrosReserva;
import co.edu.unbosque.marketplace.reservas.infraestructura.ReservaRepository;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Lo que pasa con una reserva después de confirmada: consultarla (MA-70), cancelarla con su
 * devolución (MA-72 a MA-74) y completarla al pasar la fecha de salida (MA-71).
 */
@Service
public class GestionReservasService {

	private static final Logger log = LoggerFactory.getLogger(GestionReservasService.class);
	private static final Sort MAS_RECIENTES = Sort.by(Sort.Direction.DESC, "creadoEn", "id");

	private final ReservaRepository reservaRepository;
	private final PeriodoReservadoRepository periodoReservadoRepository;
	private final EventosReserva eventos;
	private final CalculadoraDevolucion calculadora;
	private final ObjectMapper json;
	private final Clock reloj;
	private final ZoneId zonaHoraria;

	public GestionReservasService(ReservaRepository reservaRepository,
			PeriodoReservadoRepository periodoReservadoRepository, EventosReserva eventos,
			CalculadoraDevolucion calculadora, ObjectMapper json, Clock reloj,
			@Value("${marketplace.zona-horaria}") ZoneId zonaHoraria) {
		this.reservaRepository = reservaRepository;
		this.periodoReservadoRepository = periodoReservadoRepository;
		this.eventos = eventos;
		this.calculadora = calculadora;
		this.json = json;
		this.reloj = reloj;
		this.zonaHoraria = zonaHoraria;
	}

	// ─────────────────────────────────────────
	// Consultar — MA-70
	// ─────────────────────────────────────────

	/**
	 * El huésped ve sus reservas; el anfitrión, solo las de sus alojamientos; el administrador,
	 * todas. Nadie ve las de otro (MA-70, escenario 3). Las más recientes primero.
	 */
	@Transactional(readOnly = true)
	public List<ReservaDTO> listar(Usuario usuario, EstadoReserva estado) {
		Specification<Reserva> filtro = FiltrosReserva.enEstado(estado);
		filtro = switch (usuario.getRol()) {
			case HUESPED -> filtro.and(FiltrosReserva.delHuesped(usuario.getId()));
			case ANFITRION -> filtro.and(FiltrosReserva.delAnfitrion(usuario.getId()));
			case ADMINISTRADOR -> filtro;
		};
		return reservaRepository.findAll(filtro, MAS_RECIENTES).stream().map(ReservaDTO::desde).toList();
	}

	// ─────────────────────────────────────────
	// Cancelar — MA-72, MA-73, MA-74
	// ─────────────────────────────────────────

	/**
	 * El huésped o el administrador cancela la reserva. En una sola transacción la reserva queda
	 * CANCELADA con quién, cuándo, por qué y cuánto se devuelve, se liberan sus fechas y se guarda
	 * el evento: no queda una cancelación a medias (MA-74, escenario 3). La devolución queda
	 * pendiente y se pide a la pasarela fuera de la transacción ({@link CancelacionService}).
	 * <p>
	 * El anfitrión no puede cancelar (MA-74, escenario 2).
	 */
	@Transactional
	public ReservaDTO cancelar(Usuario usuario, Long reservaId, String motivo) {
		Reserva reserva = reservaRepository.buscarParaActualizar(reservaId)
				.filter(encontrada -> encontrada.esVisiblePara(usuario))
				.orElseThrow(() -> new RecursoNoEncontradoException("La reserva no existe."));
		boolean porAdministrador = usuario.getRol() == Rol.ADMINISTRADOR;
		if (!porAdministrador && !reserva.esDe(usuario.getId())) {
			throw new AccesoDenegadoException("Solo el huésped que hizo la reserva o un administrador puede cancelarla.");
		}
		Instant ahora = reloj.instant();
		reserva.cancelar(usuario.getId(), motivo, devolucion(reserva, ahora, porAdministrador), hoy(), ahora);
		periodoReservadoRepository.bloquearPorReserva(reservaId)
				.filter(fechas -> fechas.getLiberadoEn() == null)
				.ifPresent(fechas -> fechas.liberar(ahora));
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("motivo", reserva.getMotivoCancelacion());
		datos.put("canceladaPor", usuario.getRol());
		datos.put("valorDevolucion", reserva.getValorDevolucion());
		eventos.publicar("RESERVA_CANCELADA", reserva, datos);
		log.info("Reserva {} cancelada ({}); se devuelven {}", reservaId, usuario.getRol(),
				reserva.getValorDevolucion());
		return ReservaDTO.desde(reserva);
	}

	/**
	 * MA-73, escenario 1: según la política copiada al reservar y la anticipación hasta el inicio
	 * del día de llegada.
	 */
	private BigDecimal devolucion(Reserva reserva, Instant ahora, boolean porAdministrador) {
		Duration anticipacion = Duration.between(ahora,
				reserva.getFechaLlegada().atStartOfDay(zonaHoraria).toInstant());
		Map<String, Object> parametros = json.readValue(reserva.getPoliticaCancelacion(),
				new TypeReference<Map<String, Object>>() {
				});
		return calculadora.calcular(reserva.getPoliticaCancelacionCodigo(), parametros, reserva.getTotal(),
				anticipacion, porAdministrador);
	}

	// ─────────────────────────────────────────
	// Completar — MA-71
	// ─────────────────────────────────────────

	/** Las reservas confirmadas cuya fecha de salida ya llegó. */
	@Transactional(readOnly = true)
	public List<Long> porCompletar() {
		return reservaRepository.buscarPorEstadoConSalidaHasta(EstadoReserva.CONFIRMADA, hoy());
	}

	/**
	 * Completa una reserva en su propia transacción. Si mientras tanto cambió de estado (por
	 * ejemplo, se canceló), no hace nada.
	 *
	 * @return si la completó
	 */
	@Transactional
	public boolean completar(Long reservaId) {
		Reserva reserva = reservaRepository.buscarParaActualizar(reservaId).orElse(null);
		if (reserva == null || !reserva.completar(hoy(), reloj.instant())) {
			return false;
		}
		eventos.publicar("RESERVA_COMPLETADA", reserva);
		log.info("Reserva {} completada", reservaId);
		return true;
	}

	private LocalDate hoy() {
		return LocalDate.ofInstant(reloj.instant(), zonaHoraria);
	}
}
