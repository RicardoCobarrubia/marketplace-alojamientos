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

	private final ReservaRepository reservaRepository;
	private final EventosReserva eventos;
	private final Clock reloj;
	private final ZoneId zonaHoraria;

	public RevisionReservasService(ReservaRepository reservaRepository, EventosReserva eventos, Clock reloj,
			@Value("${marketplace.zona-horaria}") ZoneId zonaHoraria) {
		this.reservaRepository = reservaRepository;
		this.eventos = eventos;
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

	/**
	 * Una reserva que se acaba de confirmar mientras su alojamiento estaba bloqueado o un usuario
	 * restringido pasa a EN_REVISION, igual que las demás. Va en la transacción de la confirmación.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void ponerEnRevisionAlConfirmar(Reserva reserva, String motivo) {
		ponerEnRevision(List.of(reserva), motivo);
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
		eventos.publicar("RESERVA_MANTENIDA", reserva, EstadoReserva.EN_REVISION);
		return ReservaDTO.desde(reserva);
	}

	private int ponerEnRevision(List<Reserva> reservas, String motivo) {
		Instant ahora = reloj.instant();
		for (Reserva reserva : reservas) {
			reserva.ponerEnRevision(ahora);
			eventos.publicar("RESERVA_EN_REVISION", reserva, EstadoReserva.CONFIRMADA, Map.of("motivo", motivo));
		}
		return reservas.size();
	}

	private LocalDate hoy() {
		return LocalDate.ofInstant(reloj.instant(), zonaHoraria);
	}
}
