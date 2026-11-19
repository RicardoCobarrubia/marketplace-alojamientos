package co.edu.unbosque.marketplace.reservas.aplicacion;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import co.edu.unbosque.marketplace.notificaciones.aplicacion.PublicadorEventos;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;

/**
 * Guarda en el outbox los eventos de una reserva con los mismos datos básicos (ADR-007). Se
 * llama dentro de la transacción que cambia la reserva.
 */
@Component
public class EventosReserva {

	private static final String AGREGADO = "RESERVA";

	private final PublicadorEventos publicador;

	public EventosReserva(PublicadorEventos publicador) {
		this.publicador = publicador;
	}

	public void publicar(String tipo, Reserva reserva) {
		publicar(tipo, reserva, Map.of());
	}

	/** Con datos propios del evento, por ejemplo el motivo de una cancelación. */
	public void publicar(String tipo, Reserva reserva, Map<String, Object> adicionales) {
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
	}
}
