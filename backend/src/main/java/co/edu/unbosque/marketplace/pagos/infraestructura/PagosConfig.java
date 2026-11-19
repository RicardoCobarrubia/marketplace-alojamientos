package co.edu.unbosque.marketplace.pagos.infraestructura;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import co.edu.unbosque.marketplace.pagos.dominio.PasarelaPagos;
import tools.jackson.databind.ObjectMapper;

/**
 * Elige el adaptador de la pasarela por configuración (ADR-006): {@code simulado} o
 * {@code stripe}. Hay un solo bean {@link PasarelaPagos}, así que ningún caso de uso sabe cuál
 * se está usando, y un valor desconocido detiene el arranque.
 */
@Configuration(proxyBeanMethods = false)
public class PagosConfig {

	PagosConfig() {
		// Lo crea Spring
	}

	@Bean
	PasarelaPagos pasarelaPagos(@Value("${marketplace.pagos.proveedor:simulado}") String proveedor, ObjectMapper json,
			@Value("${marketplace.pagos.stripe.url-base:https://api.stripe.com}") String urlBase,
			@Value("${marketplace.pagos.stripe.clave-secreta:}") String claveSecreta,
			@Value("${marketplace.pagos.moneda:cop}") String moneda,
			@Value("${marketplace.pagos.stripe.metodo-pago:pm_card_visa}") String metodoPago,
			@Value("${marketplace.pagos.timeout:PT10S}") Duration timeout) {
		return switch (proveedor) {
			case "simulado" -> new PasarelaSimulada();
			case "stripe" -> new StripeAdapter(json, urlBase, claveSecreta, moneda, metodoPago, timeout);
			default -> throw new IllegalStateException(
					"marketplace.pagos.proveedor debe ser simulado o stripe, no " + proveedor + ".");
		};
	}
}
