package co.edu.unbosque.marketplace.reservas.aplicacion;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.notificaciones.aplicacion.PublicadorEventos;
import co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;

/**
 * Registra lo que le pasa a una reserva, dentro de la transacción que la cambia: el evento en el
 * outbox para las notificaciones (ADR-007) y el registro de auditoría con el estado anterior y el
 * nuevo (MA-87). Así cada cambio de estado de una reserva queda en los dos lugares, sin olvidos.
 */
@Component
public class EventosReserva {

	private static final String AGREGADO = "RESERVA";

	private final PublicadorEventos publicador;
	private final Auditoria auditoria;

	public EventosReserva(PublicadorEventos publicador, Auditoria auditoria) {
		this.publicador = publicador;
		this.auditoria = auditoria;
	}

	/** @param anterior el estado antes del cambio (el mismo si el evento no cambia el estado) */
	public void publicar(String tipo, Reserva reserva, EstadoReserva anterior) {
		publicar(tipo, reserva, anterior, Map.of());
	}

	/** Con datos propios del evento, por ejemplo el motivo de una cancelación. */
	public void publicar(String tipo, Reserva reserva, EstadoReserva anterior, Map<String, Object> adicionales) {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("reservaId", reserva.getId());
		datos.put("estado", reserva.getEstado());
		datos.put("alojamientoId", reserva.getAlojamiento().getId());
		datos.put("huespedId", reserva.getHuesped().getId());
		datos.put("anfitrionId", reserva.getAlojamiento().getAnfitrion().getId());
		datos.put("llegada", reserva.getFechaLlegada().toString());
		datos.put("salida", reserva.getFechaSalida().toString());
		datos.put("total", reserva.getTotal());
		datos.putAll(adicionales);
		publicador.publicar(tipo, AGREGADO, reserva.getId(), datos);
		Object motivo = adicionales.get("motivo");
		auditoria.registrar(EventoAuditoria.cambio(tipo, AGREGADO, reserva.getId(), anterior, reserva.getEstado(),
				motivo == null ? null : motivo.toString()));
	}
}
