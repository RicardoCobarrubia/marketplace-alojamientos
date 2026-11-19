package co.edu.unbosque.marketplace.reservas.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;
import co.edu.unbosque.marketplace.reservas.infraestructura.FiltrosReserva;
import co.edu.unbosque.marketplace.reservas.infraestructura.ReservaRepository;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;

/**
 * Lo que pasa con una reserva después de confirmada: consultarla (MA-70), cancelarla (MA-72) y
 * completarla al pasar la fecha de salida (MA-71).
 */
@Service
public class GestionReservasService {

	private static final Logger log = LoggerFactory.getLogger(GestionReservasService.class);
	private static final Sort MAS_RECIENTES = Sort.by(Sort.Direction.DESC, "creadoEn", "id");

	private final ReservaRepository reservaRepository;
	private final PeriodoReservadoRepository periodoReservadoRepository;
	private final EventosReserva eventos;
	private final Clock reloj;
	private final ZoneId zonaHoraria;

	public GestionReservasService(ReservaRepository reservaRepository,
			PeriodoReservadoRepository periodoReservadoRepository, EventosReserva eventos, Clock reloj,
			@Value("${marketplace.zona-horaria}") ZoneId zonaHoraria) {
		this.reservaRepository = reservaRepository;
		this.periodoReservadoRepository = periodoReservadoRepository;
		this.eventos = eventos;
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
	// Cancelar — MA-72
	// ─────────────────────────────────────────

	/**
	 * El huésped cancela su reserva. En una sola transacción la reserva queda CANCELADA con quién,
	 * cuándo y por qué, se liberan sus fechas y se guarda el evento: no queda una cancelación a
	 * medias. El anfitrión no puede cancelar (MA-74, escenario 2).
	 */
	@Transactional
	public ReservaDTO cancelar(Usuario usuario, Long reservaId, String motivo) {
		Reserva reserva = reservaRepository.buscarParaActualizar(reservaId)
				.filter(encontrada -> encontrada.esVisiblePara(usuario))
				.orElseThrow(() -> new RecursoNoEncontradoException("La reserva no existe."));
		if (!reserva.esDe(usuario.getId())) {
			throw new AccesoDenegadoException("Solo el huésped que hizo la reserva puede cancelarla.");
		}
		Instant ahora = reloj.instant();
		reserva.cancelar(usuario.getId(), motivo, hoy(), ahora);
		periodoReservadoRepository.bloquearPorReserva(reservaId)
				.filter(fechas -> fechas.getLiberadoEn() == null)
				.ifPresent(fechas -> fechas.liberar(ahora));
		eventos.publicar("RESERVA_CANCELADA", reserva, Map.of("motivo", reserva.getMotivoCancelacion()));
		log.info("Reserva {} cancelada por el huésped", reservaId);
		return ReservaDTO.desde(reserva);
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
