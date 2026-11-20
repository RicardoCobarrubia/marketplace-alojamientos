package co.edu.unbosque.marketplace.comun.auditoria;

/**
 * Un hecho que queda en el registro de auditoría (MA-87). El actor no va aquí: lo toma la
 * implementación del usuario autenticado en la petición.
 *
 * @param accion qué pasó, por ejemplo {@code BLOQUEAR_ALOJAMIENTO}
 * @param entidad tipo de lo que cambió, por ejemplo {@code ALOJAMIENTO}; puede ser nulo
 * @param entidadId identificador de lo que cambió; puede ser nulo
 * @param estadoAnterior estado antes del cambio; puede ser nulo
 * @param estadoNuevo estado después del cambio; puede ser nulo
 * @param resultado si la acción se hizo o se rechazó
 * @param detalle texto libre sin contraseñas ni códigos, por ejemplo el motivo
 */
public record EventoAuditoria(String accion, String entidad, Long entidadId, String estadoAnterior,
		String estadoNuevo, Resultado resultado, String detalle) {

	public enum Resultado {
		EXITO,
		RECHAZADO
	}

	/** Un cambio de estado hecho con éxito. */
	public static EventoAuditoria cambio(String accion, String entidad, Long entidadId, Object anterior, Object nuevo,
			String detalle) {
		return new EventoAuditoria(accion, entidad, entidadId, texto(anterior), texto(nuevo), Resultado.EXITO, detalle);
	}

	/** Alguien intentó algo que su rol no permite (MA-80 y MA-81, escenario 3). */
	public static EventoAuditoria accesoDenegado(String recurso) {
		return new EventoAuditoria("ACCESO_DENEGADO", null, null, null, null, Resultado.RECHAZADO, recurso);
	}

	private static String texto(Object valor) {
		return valor == null ? null : valor.toString();
	}
}
