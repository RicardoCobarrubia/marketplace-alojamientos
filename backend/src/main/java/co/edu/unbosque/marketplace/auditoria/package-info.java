/**
 * Módulo de auditoría: guarda los eventos de {@link co.edu.unbosque.marketplace.comun.auditoria.Auditoria}
 * en la tabla {@code auditoria}, que la base de datos protege contra cambios y borrados (MA-87).
 * <p>
 * Sigue las capas del estilo elegido (ADR-001); solo tiene {@code dominio} e
 * {@code infraestructura} porque no expone servicios ni API propios.
 */
package co.edu.unbosque.marketplace.auditoria;
