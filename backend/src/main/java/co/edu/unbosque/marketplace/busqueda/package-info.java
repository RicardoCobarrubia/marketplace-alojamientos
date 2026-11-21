/**
 * Módulo de búsqueda (MA-61): el huésped busca alojamientos activos por ciudad y los filtra por
 * fechas, huéspedes, tipo, precio y servicios. Con fechas solo aparecen los que tienen todo el
 * rango dentro de un periodo disponible y sin reservas que se crucen. Vive aparte porque combina
 * alojamientos y disponibilidad, y disponibilidad ya depende de alojamientos.
 * <p>
 * Sigue las capas del estilo elegido (ADR-001).
 */
package co.edu.unbosque.marketplace.busqueda;
