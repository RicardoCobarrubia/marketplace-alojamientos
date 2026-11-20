package co.edu.unbosque.marketplace.reservas.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.comun.excepcion.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;
import co.edu.unbosque.marketplace.reservas.infraestructura.FiltrosReserva;
import co.edu.unbosque.marketplace.reservas.infraestructura.ReservaRepository;

/**
 * Reservas EN_REVISION por moderación (MA-80 a MA-82): las confirmadas de un alojamiento
 * bloqueado o de un usuario restringido pasan a revisión sin cancelarse, y el administrador
 * decide si se mantienen. Cancelarlas es la cancelación normal hecha por el administrador, con
 * devolución del 100 % ({@link CancelacionService}).
 */
@Service
public class RevisionReservasService {

	private static final String RESERVA = "RESERVA";

	private final ReservaRepository reservaRepository;
	private final EventosReserva eventos;
	private final Auditoria auditoria;
	private final Clock reloj;
	private final ZoneId zonaHoraria;

	public RevisionReservasService(ReservaRepository reservaRepository, EventosReserva eventos, Auditoria auditoria,
			Clock reloj, @Value("${marketplace.zona-horaria}") ZoneId zonaHoraria) {
		this.reservaRepository = reservaRepository;
		this.eventos = eventos;
		this.auditoria = auditoria;
		this.reloj = reloj;
		this.zonaHoraria = zonaHoraria;
	}

	/**
	 * Las reservas confirmadas que aún no empiezan del alojamiento pasan a EN_REVISION. Va en la
	 * transacción del bloqueo.
	 *
	 * @return cuántas pasaron a revisión
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public int ponerEnRevisionDelAlojamiento(Long alojamientoId, String motivo) {
		return ponerEnRevision(reservaRepository.bloquearDelAlojamientoDesde(alojamientoId, EstadoReserva.CONFIRMADA,
				hoy()), motivo);
	}

	/**
	 * Las reservas confirmadas que aún no empiezan del usuario, como huésped o como anfitrión,
	 * pasan a EN_REVISION. Va en la transacción de la restricción.
	 *
	 * @return cuántas pasaron a revisión
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public int ponerEnRevisionDelUsuario(Long usuarioId, String motivo) {
		return ponerEnRevision(reservaRepository.bloquearDelUsuarioDesde(usuarioId, EstadoReserva.CONFIRMADA, hoy()),
				motivo);
	}

	/** MA-82, escenario 1: las reservas EN_REVISION, las más recientes primero. */
	@Transactional(readOnly = true)
	public List<ReservaDTO> enRevision() {
		return reservaRepository.findAll(FiltrosReserva.enEstado(EstadoReserva.EN_REVISION),
				Sort.by(Sort.Direction.DESC, "creadoEn", "id")).stream().map(ReservaDTO::desde).toList();
	}

	/** MA-82, escenario 2: el administrador la mantiene y vuelve a CONFIRMADA. */
	@Transactional
	public ReservaDTO mantener(Long reservaId) {
		Reserva reserva = reservaRepository.buscarParaActualizar(reservaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("La reserva no existe."));
		reserva.mantener(reloj.instant());
		eventos.publicar("RESERVA_MANTENIDA", reserva);
		auditoria.registrar(EventoAuditoria.cambio("MANTENER_RESERVA", RESERVA, reservaId, EstadoReserva.EN_REVISION,
				EstadoReserva.CONFIRMADA, null));
		return ReservaDTO.desde(reserva);
	}

	private int ponerEnRevision(List<Reserva> reservas, String motivo) {
		Instant ahora = reloj.instant();
		for (Reserva reserva : reservas) {
			reserva.ponerEnRevision(ahora);
			eventos.publicar("RESERVA_EN_REVISION", reserva, Map.of("motivo", motivo));
			auditoria.registrar(EventoAuditoria.cambio("RESERVA_EN_REVISION", RESERVA, reserva.getId(),
					EstadoReserva.CONFIRMADA, EstadoReserva.EN_REVISION, motivo));
		}
		return reservas.size();
	}

	private LocalDate hoy() {
		return LocalDate.ofInstant(reloj.instant(), zonaHoraria);
	}
}
