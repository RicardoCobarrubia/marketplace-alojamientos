/**
 * Módulo de moderación (MA-80 a MA-82): el administrador bloquea y desbloquea alojamientos,
 * restringe usuarios y revisa las reservas que esas acciones dejan EN_REVISION. Coordina los
 * módulos de alojamientos, usuarios y reservas sin que ellos dependan entre sí, y deja cada
 * acción en el registro de auditoría.
 * <p>
 * Sigue las capas del estilo elegido (ADR-001); solo tiene {@code presentacion} y
 * {@code aplicacion} porque no tiene entidades propias.
 */
package co.edu.unbosque.marketplace.moderacion;
