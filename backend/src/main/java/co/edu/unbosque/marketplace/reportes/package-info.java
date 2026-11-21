/**
 * Módulo de reportes de gestión (MA-85, MA-86): el administrador exporta en PDF o Excel los
 * usuarios, alojamientos, reservas, ingresos estimados y demanda por ciudad y tipo de un período.
 * Cada exportación queda en la auditoría antes de generarse, así que existe aunque falle.
 * <p>
 * Sigue las capas del estilo elegido (ADR-001). El contenido del reporte ({@code dominio}) no
 * depende del formato: los generadores de {@code infraestructura} lo convierten en PDF o Excel.
 */
package co.edu.unbosque.marketplace.reportes;
