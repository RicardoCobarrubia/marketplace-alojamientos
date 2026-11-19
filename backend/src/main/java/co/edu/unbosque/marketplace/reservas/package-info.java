/**
 * Módulo de reservas: solicitud, pago simulado, consistencia ante fallos de la pasarela y
 * reintentos (MA-66 a MA-69, MA-60).
 * <p>
 * Sigue el flujo del ADR-002: transacción 1 (clave de idempotencia, reserva en PENDIENTE_PAGO y
 * fechas retenidas), cobro fuera de transacción con límite de 10 s (ADR-004) y transacción 2
 * (confirmación o compensación con su evento en el outbox, ADR-007). La reserva usa la
 * pasarela solo a través del puerto {@code PasarelaPagos} (ADR-006).
 * <p>
 * Cada módulo de negocio se organiza en las capas del estilo elegido (ADR-001):
 * {@code presentacion} (controladores REST), {@code aplicacion} (servicios, transacciones y
 * DTO), {@code dominio} (entidades y reglas) e {@code infraestructura} (repositorios y
 * adaptadores).
 */
package co.edu.unbosque.marketplace.reservas;
