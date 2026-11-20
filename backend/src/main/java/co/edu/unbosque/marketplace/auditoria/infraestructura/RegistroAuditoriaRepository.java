package co.edu.unbosque.marketplace.auditoria.infraestructura;

import java.util.List;

import org.springframework.data.repository.Repository;

import co.edu.unbosque.marketplace.auditoria.dominio.RegistroAuditoria;

/**
 * Solo permite insertar y consultar: no ofrece métodos para cambiar ni borrar registros (MA-87,
 * escenario 3). Las consultas devuelven como máximo los 200 más recientes.
 */
public interface RegistroAuditoriaRepository extends Repository<RegistroAuditoria, Long> {

	RegistroAuditoria save(RegistroAuditoria registro);

	List<RegistroAuditoria> findTop200ByOrderByIdDesc();

	List<RegistroAuditoria> findTop200ByEntidadAndEntidadIdOrderByIdDesc(String entidad, Long entidadId);

	List<RegistroAuditoria> findTop200ByActorIdOrderByIdDesc(Long actorId);

	List<RegistroAuditoria> findTop200ByAccionOrderByIdDesc(String accion);
}
