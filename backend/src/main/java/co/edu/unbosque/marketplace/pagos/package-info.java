/**
 * Módulo de pagos con puertos y adaptadores (ADR-006). Es el único módulo de negocio donde se
 * invierte la dependencia:
 * <ul>
 * <li>{@code dominio}: el puerto {@code PasarelaPagos}, en términos del negocio.</li>
 * <li>{@code infraestructura}: {@code StripeAdapter} (Stripe en modo pruebas) y el adaptador
 * simulado para pruebas.</li>
 * </ul>
 * Ninguna clase de dominio o aplicación importa el SDK de Stripe: lo comprueba
 * {@code ArquitecturaTest}. Se implementa con la historia de reservas (MA-67).
 */
package co.edu.unbosque.marketplace.pagos;
