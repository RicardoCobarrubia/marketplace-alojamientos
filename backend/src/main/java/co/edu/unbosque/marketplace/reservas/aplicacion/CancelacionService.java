package co.edu.unbosque.marketplace.reservas.aplicacion;

import java.util.List;
import java.util.OptionalInt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import co.edu.unbosque.marketplace.pagos.dominio.PasarelaNoDisponibleException;
import co.edu.unbosque.marketplace.pagos.dominio.PasarelaPagos;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;

/**
 * Cancelación con devolución (MA-72 a MA-74). Igual que en el pago (ADR-002), la llamada a la
 * pasarela queda fuera de la transacción:
 * <ol>
 * <li>Una transacción deja la reserva CANCELADA, con el valor a devolver y la devolución
 * pendiente, y libera las fechas ({@link GestionReservasService#cancelar}).</li>
 * <li>Después se pide la devolución a la pasarela de pruebas. Si no responde, la devolución queda
 * pendiente y el conciliador la reintenta: la cancelación nunca queda a medias.</li>
 * </ol>
 */
@Service
public class CancelacionService {

	private static final Logger log = LoggerFactory.getLogger(CancelacionService.class);

	private final GestionReservasService gestion;
	private final ReservaTransacciones transacciones;
	private final PasarelaPagos pasarela;

	public CancelacionService(GestionReservasService gestion, ReservaTransacciones transacciones,
			PasarelaPagos pasarela) {
		this.gestion = gestion;
		this.transacciones = transacciones;
		this.pasarela = pasarela;
	}

	public ReservaDTO cancelar(Usuario usuario, Long reservaId, String motivo) {
		ReservaDTO cancelada = gestion.cancelar(usuario, reservaId, motivo);
		if (!Boolean.TRUE.equals(cancelada.devolucionPendiente())) {
			return cancelada;
		}
		try {
			devolver(reservaId);
		} catch (PasarelaNoDisponibleException e) {
			log.warn("La pasarela no aceptó la devolución de la reserva {}; se reintentará: {}", reservaId,
					e.getMessage());
			return cancelada;
		}
		return transacciones.obtener(usuario, reservaId);
	}

	/** Las cancelaciones cuya devolución sigue pendiente. */
	public List<Long> devolucionesPendientes() {
		return transacciones.devolucionesPendientes();
	}

	/**
	 * Pide a la pasarela la devolución pendiente de una reserva cancelada. Antes la reclama: si
	 * otra ejecución la está pidiendo, no hace nada, así dos ejecuciones a la vez no la piden las
	 * dos. La pasarela solo devuelve lo que falta.
	 * <p>
	 * Si la pasarela no respondió, el reclamo vence solo y el reintento usa la misma clave: si la
	 * petición anterior seguía en curso, la pasarela no la repite. Si respondió con un error, el
	 * reintento usa una clave nueva.
	 *
	 * @return si se pidió la devolución
	 * @throws PasarelaNoDisponibleException si la pasarela no la aceptó; sigue pendiente
	 */
	public boolean devolver(Long reservaId) {
		OptionalInt intento = transacciones.reclamarDevolucion(reservaId);
		if (intento.isEmpty()) {
			return false;
		}
		Reserva reserva = transacciones.buscar(reservaId)
				.orElseThrow(() -> new IllegalStateException("La reserva " + reservaId + " no existe."));
		try {
			pasarela.devolver(reserva.getReferenciaPago(), reserva.getValorDevolucion(),
					"reserva-" + reservaId + "-cancelacion", intento.getAsInt());
		} catch (PasarelaNoDisponibleException e) {
			transacciones.registrarFalloDevolucion(reservaId, e.puedeSeguirEnCurso());
			throw e;
		}
		transacciones.marcarDevolucionSolicitada(reservaId);
		log.info("Devolución solicitada para la reserva cancelada {}", reserva.getId());
		return true;
	}
}
