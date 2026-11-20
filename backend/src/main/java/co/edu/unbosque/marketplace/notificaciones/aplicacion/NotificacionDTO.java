package co.edu.unbosque.marketplace.notificaciones.aplicacion;

import java.time.Instant;

import co.edu.unbosque.marketplace.notificaciones.dominio.Notificacion;

/** Una notificación del usuario (MA-76). */
public record NotificacionDTO(Long id, String tipo, String titulo, String mensaje, Instant creadaEn, Instant leidaEn) {

	public static NotificacionDTO desde(Notificacion notificacion) {
		return new NotificacionDTO(notificacion.getId(), notificacion.getTipo(), notificacion.getTitulo(),
				notificacion.getMensaje(), notificacion.getCreadaEn(), notificacion.getLeidaEn());
	}
}
