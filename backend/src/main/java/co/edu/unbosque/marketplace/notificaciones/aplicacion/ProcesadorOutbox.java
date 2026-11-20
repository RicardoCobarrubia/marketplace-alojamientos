package co.edu.unbosque.marketplace.notificaciones.aplicacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import co.edu.unbosque.marketplace.notificaciones.infraestructura.EventoOutboxRepository;
import co.edu.unbosque.marketplace.notificaciones.infraestructura.NotificacionRepository;

/**
 * Procesador del Transactional Outbox (ADR-007): cada pocos segundos entrega los eventos
 * pendientes. Corre aparte de las reservas y cancelaciones: si las notificaciones fallan o están
 * fuera de servicio, esas operaciones se completan igual y los eventos esperan en el outbox
 * (MA-75, escenario 3).
 */
@Component
public class ProcesadorOutbox {

	private static final Logger log = LoggerFactory.getLogger(ProcesadorOutbox.class);
	private static final int LOTE = 50;

	private final EventoOutboxRepository eventos;
	private final EntregaEventos entrega;
	private final NotificacionRepository notificaciones;
	private final EnvioCorreos correos;

	public ProcesadorOutbox(EventoOutboxRepository eventos, EntregaEventos entrega,
			NotificacionRepository notificaciones, EnvioCorreos correos) {
		this.eventos = eventos;
		this.entrega = entrega;
		this.notificaciones = notificaciones;
		this.correos = correos;
	}

	/** @return cuántos eventos entregó */
	@Scheduled(fixedDelayString = "${marketplace.notificaciones.intervalo:PT10S}",
			initialDelayString = "${marketplace.notificaciones.intervalo:PT10S}")
	public int procesarPendientes() {
		int entregados = 0;
		for (Long eventoId : eventos.idsPendientes(LOTE)) {
			try {
				if (entrega.entregar(eventoId)) {
					entregados++;
				}
			} catch (RuntimeException e) {
				// Un evento con problemas no detiene a los demás
				log.warn("No se pudo entregar el evento {}: {}", eventoId, e.getMessage());
				entrega.registrarFallo(eventoId, e.getClass().getSimpleName() + ": " + e.getMessage());
			}
		}
		return entregados;
	}

	/**
	 * Envía los correos pendientes, aparte de la entrega de eventos: si el servidor de correo
	 * está caído, las notificaciones internas se siguen generando.
	 *
	 * @return cuántos correos envió
	 */
	@Scheduled(fixedDelayString = "${marketplace.notificaciones.intervalo:PT10S}",
			initialDelayString = "${marketplace.notificaciones.intervalo:PT10S}")
	public int enviarCorreosPendientes() {
		int enviados = 0;
		for (Long notificacionId : notificaciones.idsConCorreoPendiente(LOTE)) {
			try {
				if (correos.enviar(notificacionId)) {
					enviados++;
				}
			} catch (RuntimeException e) {
				log.warn("No se pudo enviar el correo de la notificación {}: {}", notificacionId, e.getMessage());
				correos.registrarFallo(notificacionId);
			}
		}
		return enviados;
	}
}
