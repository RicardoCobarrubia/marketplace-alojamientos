/**
 * Integración de pagos con puertos y adaptadores (ADR-006): el puerto
 * {@code PasarelaPagos} y sus adaptadores {@code StripeAdapter} (Stripe en modo pruebas)
 * y {@code SimuladorPagosAdapter}. El resto del sistema solo conoce el puerto.
 */
package co.edu.unbosque.marketplace.pagos;
