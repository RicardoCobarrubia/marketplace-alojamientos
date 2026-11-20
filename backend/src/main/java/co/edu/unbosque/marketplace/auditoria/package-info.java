/**
 * Módulo de auditoría (MA-87): guarda los eventos de
 * {@link co.edu.unbosque.marketplace.comun.auditoria.Auditoria} en la tabla {@code auditoria}, que
 * la base de datos protege contra cambios, borrados y vaciados, y deja consultarlos al
 * administrador.
 * <p>
 * Se registran los inicios de sesión y registros de usuarios, los cambios de estado de
 * alojamientos y reservas, las cancelaciones, las acciones administrativas, los accesos
 * denegados y los errores inesperados, con actor, acción, entidad, estados, resultado e id de
 * correlación, en la misma transacción que el cambio. Nunca se guardan contraseñas ni códigos.
 * <p>
 * Sigue las capas del estilo elegido (ADR-001); el puerto está en {@code comun} para que
 * cualquier módulo pueda registrar sin depender de este.
 */
package co.edu.unbosque.marketplace.auditoria;
