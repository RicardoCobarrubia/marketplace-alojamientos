/**
 * Seguridad de la API con la misma estructura de Software 1: {@code SecurityConfig},
 * {@code JwtUtil}, {@code JwtAuthenticationFilter} y {@code UserDetailsServiceImpl}.
 * <p>
 * De Software 2 agrega la revocación de sesión con caché Caffeine de 60 s (ADR-010) y las
 * respuestas 401/403 en JSON. Junto con el paquete {@code filter} forma la CadenaDeFiltros
 * del diagrama de despliegue (ADR-009).
 */
package co.edu.unbosque.marketplace.security;
