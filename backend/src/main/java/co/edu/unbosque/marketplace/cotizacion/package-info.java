/**
 * Módulo de cotización: calcula el costo de una estadía con su desglose (MA-63, MA-64) y guarda
 * las reglas que configura el administrador (MA-65).
 * <p>
 * Los descuentos siguen el patrón Strategy ({@code ReglaDescuento}): un tipo de descuento nuevo
 * es una clase más, sin cambiar la calculadora.
 * <p>
 * Cada módulo de negocio se organiza en las capas del estilo elegido (ADR-001):
 * {@code presentacion} (controladores REST), {@code aplicacion} (servicios, transacciones y
 * DTO), {@code dominio} (entidades y reglas) e {@code infraestructura} (repositorios y
 * adaptadores).
 */
package co.edu.unbosque.marketplace.cotizacion;
