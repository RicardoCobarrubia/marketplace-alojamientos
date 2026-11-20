package co.edu.unbosque.marketplace.comun.auditoria;

/**
 * Un hecho que queda en el registro de auditoría (MA-87). Nunca lleva contraseñas, tokens ni
 * códigos. Si no trae actor, la implementación toma el usuario autenticado de la petición; en
 * una tarea programada no hay actor.
 *
 * @param accion qué pasó, por ejemplo {@code BLOQUEAR_ALOJAMIENTO}
 * @param entidad tipo de lo que cambió, por ejemplo {@code ALOJAMIENTO}; puede ser nulo
 * @param entidadId identificador de lo que cambió; puede ser nulo
 * @param estadoAnterior estado antes del cambio; puede ser nulo
 * @param estadoNuevo estado después del cambio; puede ser nulo
 * @param resultado si la acción se hizo, se rechazó o falló
 * @param detalle texto libre sin datos sensibles, por ejemplo el motivo
 * @param actorId quién lo hizo, cuando todavía no hay sesión (registro, inicio de sesión)
 * @param actorRol el rol de ese actor
 */
public record EventoAuditoria(String accion, String entidad, Long entidadId, String estadoAnterior,
		String estadoNuevo, Resultado resultado, String detalle, Long actorId, String actorRol) {

	public enum Resultado {
		EXITO,
		RECHAZADO,
		/** Error inesperado del sistema. */
		ERROR
	}

	/** Un cambio de estado hecho con éxito. */
	public static EventoAuditoria cambio(String accion, String entidad, Long entidadId, Object anterior, Object nuevo,
			String detalle) {
		return new EventoAuditoria(accion, entidad, entidadId, texto(anterior), texto(nuevo), Resultado.EXITO, detalle,
				null, null);
	}

	/** Una acción sin cambio de estado, por ejemplo un alta en el catálogo. */
	public static EventoAuditoria accion(String accion, String entidad, Long entidadId, Resultado resultado,
			String detalle) {
		return new EventoAuditoria(accion, entidad, entidadId, null, null, resultado, detalle, null, null);
	}

	/** Alguien intentó algo que su rol no permite (MA-80 y MA-81, escenario 3). */
	public static EventoAuditoria accesoDenegado(String recurso) {
		return accion("ACCESO_DENEGADO", null, null, Resultado.RECHAZADO, recurso);
	}

	/** Un error inesperado: solo el recurso y el tipo de error, nunca el mensaje ni los datos. */
	public static EventoAuditoria error(String recurso, String tipoError) {
		return accion("ERROR_INESPERADO", null, null, Resultado.ERROR, recurso + " (" + tipoError + ")");
	}

	/** El mismo evento con un actor explícito. */
	public EventoAuditoria conActor(Long id, Object rol) {
		return new EventoAuditoria(accion, entidad, entidadId, estadoAnterior, estadoNuevo, resultado, detalle, id,
				texto(rol));
	}

	private static String texto(Object valor) {
		return valor == null ? null : valor.toString();
	}
}
