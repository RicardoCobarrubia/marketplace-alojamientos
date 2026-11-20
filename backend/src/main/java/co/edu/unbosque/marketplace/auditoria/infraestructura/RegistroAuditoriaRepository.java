package co.edu.unbosque.marketplace.auditoria.infraestructura;

import org.springframework.data.repository.Repository;

import co.edu.unbosque.marketplace.auditoria.dominio.RegistroAuditoria;

/** Solo permite insertar: no ofrece métodos para cambiar ni borrar registros (MA-87, escenario 3). */
public interface RegistroAuditoriaRepository extends Repository<RegistroAuditoria, Long> {

	RegistroAuditoria save(RegistroAuditoria registro);
}
