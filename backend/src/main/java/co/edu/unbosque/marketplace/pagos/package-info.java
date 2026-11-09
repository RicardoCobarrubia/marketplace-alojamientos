/**
 * Módulo de pagos con puertos y adaptadores (ADR-006). Es el único módulo de negocio donde se
 * invierte la dependencia:
 * <ul>
 * <li>{@code dominio}: el puerto {@code PasarelaPagos}, en términos del negocio.</li>
 * <li>{@code infraestructura}: {@code StripeAdapter} (Stripe en modo pruebas) y
 * {@code PasarelaSimulada}.</li>
 * </ul>
 * El proveedor se elige con {@code marketplace.pagos.proveedor} ({@code simulado} o
 * {@code stripe}). Ninguna clase de dominio o aplicación conoce Stripe: lo comprueba
 * {@code ArquitecturaTest}.
 */
package co.edu.unbosque.marketplace.pagos;
