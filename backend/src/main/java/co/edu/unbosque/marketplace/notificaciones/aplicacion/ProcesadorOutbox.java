package co.edu.unbosque.marketplace.notificaciones.aplicacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import co.edu.unbosque.marketplace.notificaciones.infraestructura.EventoOutboxRepository;

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

	public ProcesadorOutbox(EventoOutboxRepository eventos, EntregaEventos entrega) {
		this.eventos = eventos;
		this.entrega = entrega;
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
}
