/**
 * Módulo de registro, inicio de sesión y perfil (MA-47, MA-48, MA-49).
 * <p>
 * En {@code infraestructura.seguridad} está el filtro central de autorización por rol
 * (ADR-009) y la revocación de sesión por versión (ADR-010).
 * <p>
 * Cada módulo de negocio se organiza en las capas del estilo elegido (ADR-001):
 * {@code presentacion} (controladores REST), {@code aplicacion} (servicios, transacciones y
 * DTO), {@code dominio} (entidades y reglas) e {@code infraestructura} (repositorios y
 * adaptadores).
 */
package co.edu.unbosque.marketplace.usuarios;
