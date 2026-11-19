/**
 * Módulo de notificaciones internas: el puerto de envío de correo con su adaptador SMTP y el
 * procesador del Transactional Outbox (ADR-007).
 * <p>
 * Por ahora solo se guardan los eventos: {@code PublicadorEventos} los escribe en la misma
 * transacción que el cambio que los origina. La entrega y las notificaciones internas van en su
 * propia historia.
 */
package co.edu.unbosque.marketplace.notificaciones;
