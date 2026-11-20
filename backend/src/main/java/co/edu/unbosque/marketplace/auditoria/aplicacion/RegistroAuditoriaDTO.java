package co.edu.unbosque.marketplace.auditoria.aplicacion;

import java.time.Instant;

import co.edu.unbosque.marketplace.auditoria.dominio.RegistroAuditoria;

/** Un registro de auditoría para el administrador (MA-87). */
public record RegistroAuditoriaDTO(Long id, Instant ocurridoEn, Long actorId, String actorRol, String accion,
		String entidad, Long entidadId, String estadoAnterior, String estadoNuevo, String resultado, String detalle,
		String correlationId) {

	public static RegistroAuditoriaDTO desde(RegistroAuditoria registro) {
		return new RegistroAuditoriaDTO(registro.getId(), registro.getOcurridoEn(), registro.getActorId(),
				registro.getActorRol(), registro.getAccion(), registro.getEntidad(), registro.getEntidadId(),
				registro.getEstadoAnterior(), registro.getEstadoNuevo(), registro.getResultado().name(),
				registro.getDetalle(), registro.getCorrelationId());
	}
}
