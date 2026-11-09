/**
 * Módulo de disponibilidad: periodos que define el anfitrión, fechas reservadas y consulta
 * de disponibilidad (MA-58, MA-59). La regla de no superposición la garantiza la base de datos
 * con una restricción de exclusión (ADR-003).
 * <p>
 * Cada módulo de negocio se organiza en las capas del estilo elegido (ADR-001):
 * {@code presentacion} (controladores REST), {@code aplicacion} (servicios, transacciones y
 * DTO), {@code dominio} (entidades y reglas) e {@code infraestructura} (repositorios y
 * adaptadores).
 */
package co.edu.unbosque.marketplace.disponibilidad;
