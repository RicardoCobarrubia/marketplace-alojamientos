/**
 * Módulo de notificaciones internas (MA-75, MA-76) y procesador del Transactional Outbox
 * (ADR-007).
 * <p>
 * {@code PublicadorEventos} guarda cada evento en la misma transacción que el cambio que lo
 * origina. {@code ProcesadorOutbox} los entrega después, por separado: genera las notificaciones
 * según {@code ReglasNotificacion} y marca el evento ENTREGADO. Así un fallo de las
 * notificaciones nunca impide reservar ni cancelar.
 */
package co.edu.unbosque.marketplace.notificaciones;
