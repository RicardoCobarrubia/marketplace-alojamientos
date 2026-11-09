package co.edu.unbosque.marketplace.reservas.aplicacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import co.edu.unbosque.marketplace.pagos.dominio.PasarelaNoDisponibleException;

/**
 * Tarea programada que deja todas las reservas en un estado final conocido (ADR-002, ADR-004):
 * <ul>
 * <li>Reservas en PENDIENTE_PAGO cuya retención de 10 minutos venció: se compensan y, si se
 * alcanzó a cobrar, se devuelve el pago (MA-60, escenario 2).</li>
 * <li>Pagos en duda (la pasarela no respondió ni a la consulta): se consultan otra vez y, si hubo
 * cobro, se devuelve (MA-68, escenario 3).</li>
 * </ul>
 * Puede correr en varias réplicas a la vez: cada reserva se bloquea al cambiarla y las
 * operaciones con la pasarela usan la misma clave de idempotencia.
 */
@Component
public class ConciliadorReservas {

	private static final Logger log = LoggerFactory.getLogger(ConciliadorReservas.class);

	private final ReservaTransacciones transacciones;
	private final ReservaService reservaService;

	public ConciliadorReservas(ReservaTransacciones transacciones, ReservaService reservaService) {
		this.transacciones = transacciones;
		this.reservaService = reservaService;
	}

	@Scheduled(fixedDelayString = "${marketplace.reservas.conciliacion:PT30S}",
			initialDelayString = "${marketplace.reservas.conciliacion:PT30S}")
	public void conciliar() {
		vencerRetenciones();
		verificarPagosEnDuda();
	}

	public int vencerRetenciones() {
		int resueltas = 0;
		for (Long reservaId : transacciones.pendientesVencidas()) {
			try {
				transacciones.buscar(reservaId).ifPresent(reservaService::vencer);
				resueltas++;
			} catch (RuntimeException e) {
				log.error("No se pudo vencer la retención de la reserva {}", reservaId, e);
			}
		}
		return resueltas;
	}

	public int verificarPagosEnDuda() {
		int verificadas = 0;
		for (Long reservaId : transacciones.pagosPorVerificar()) {
			try {
				transacciones.buscar(reservaId).ifPresent(reservaService::verificarPago);
				verificadas++;
			} catch (PasarelaNoDisponibleException e) {
				log.warn("La pasarela sigue sin responder para la reserva {}", reservaId);
			}
		}
		return verificadas;
	}
}
