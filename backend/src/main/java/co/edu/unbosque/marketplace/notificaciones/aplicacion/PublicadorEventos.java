package co.edu.unbosque.marketplace.notificaciones.aplicacion;

import java.time.Clock;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.comun.web.CorrelacionFilter;
import co.edu.unbosque.marketplace.notificaciones.dominio.EventoOutbox;
import co.edu.unbosque.marketplace.notificaciones.infraestructura.EventoOutboxRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Guarda un evento en el outbox (ADR-007). Exige una transacción abierta: el evento se confirma
 * o se revierte junto con el cambio que lo origina. La entrega a las notificaciones internas la
 * hace el procesador del outbox, en su propia historia.
 */
@Service
public class PublicadorEventos {

	private final EventoOutboxRepository repositorio;
	private final ObjectMapper json;
	private final Clock reloj;

	public PublicadorEventos(EventoOutboxRepository repositorio, ObjectMapper json, Clock reloj) {
		this.repositorio = repositorio;
		this.json = json;
		this.reloj = reloj;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void publicar(String tipo, String agregado, Long agregadoId, Map<String, Object> datos) {
		repositorio.save(new EventoOutbox(tipo, agregado, agregadoId, json.writeValueAsString(datos),
				MDC.get(CorrelacionFilter.CLAVE_MDC), reloj.instant()));
	}
}
