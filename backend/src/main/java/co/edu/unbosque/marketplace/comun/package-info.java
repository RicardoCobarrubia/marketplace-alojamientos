/**
 * Código transversal que usan todos los módulos y que no pertenece a ninguno.
 * <ul>
 * <li>{@code configuracion}: caché, reloj, OpenAPI y mapeo.</li>
 * <li>{@code excepcion}: excepciones de negocio comunes ({@code ExcepcionMarketplace} y derivadas).</li>
 * <li>{@code web}: id de correlación por petición (ADR-012) y traducción de errores a
 * Problem Details.</li>
 * </ul>
 * No depende de ningún módulo de negocio.
 */
package co.edu.unbosque.marketplace.comun;
