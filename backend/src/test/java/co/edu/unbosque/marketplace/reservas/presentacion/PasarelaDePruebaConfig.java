package co.edu.unbosque.marketplace.reservas.presentacion;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Reemplaza la pasarela por {@link PasarelaDePrueba} en las pruebas de integración. Todas las que
 * la usan importan esta misma clase, así comparten el contexto de Spring.
 */
@TestConfiguration
public class PasarelaDePruebaConfig {

	@Bean
	@Primary
	PasarelaDePrueba pasarelaDePrueba() {
		return new PasarelaDePrueba();
	}
}
