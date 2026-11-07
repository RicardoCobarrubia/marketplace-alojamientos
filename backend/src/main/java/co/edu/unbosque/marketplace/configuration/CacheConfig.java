package co.edu.unbosque.marketplace.configuration;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Activa la caché Caffeine en memoria de cada réplica.
 * <p>
 * Guarda la versión de sesión de cada usuario para no consultar la base de datos en cada
 * petición. El tiempo de vida está en {@code spring.cache.caffeine.spec}: como máximo 60 s,
 * que es lo que tarda en hacerse efectiva una revocación de sesión (ADR-010, ASR-08).
 */
@Configuration
@EnableCaching
public class CacheConfig {

	/** Nombre de la caché de versiones de sesión, declarado en {@code spring.cache.cache-names}. */
	public static final String VERSIONES_SESION = "versionesSesion";
}
