package co.edu.unbosque.marketplace.configuration;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicación en UTC. Los servicios lo reciben inyectado en lugar de llamar a
 * {@code Instant.now()}, así las pruebas pueden fijar la hora (vencimientos, bloqueos, retenciones).
 */
@Configuration
public class RelojConfig {

	@Bean
	public Clock reloj() {
		return Clock.systemUTC();
	}
}
