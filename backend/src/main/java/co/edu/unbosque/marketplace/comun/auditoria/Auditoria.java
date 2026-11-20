package co.edu.unbosque.marketplace.comun.auditoria;

/**
 * Puerto del registro de auditoría (MA-87). Está en {@code comun} para que cualquier módulo,
 * incluidos los manejadores de errores y de seguridad, pueda registrar sin depender del módulo
 * que lo implementa.
 * <p>
 * Si se llama dentro de una transacción, el registro se guarda en esa misma transacción: si el
 * cambio se deshace, su registro también.
 */
public interface Auditoria {

	void registrar(EventoAuditoria evento);
}
