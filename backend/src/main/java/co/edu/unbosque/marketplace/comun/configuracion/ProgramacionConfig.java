package co.edu.unbosque.marketplace.comun.configuracion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas programadas, como el conciliador de reservas (MA-60). Las pruebas la
 * desactivan con {@code marketplace.tareas-programadas=false} y llaman las tareas directamente.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "marketplace.tareas-programadas", havingValue = "true", matchIfMissing = true)
public class ProgramacionConfig {

	ProgramacionConfig() {
		// Lo crea Spring
	}
}
