package co.edu.unbosque.marketplace.pagos.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class PagosConfigTest {

	private final PagosConfig config = new PagosConfig();
	private final JsonMapper json = JsonMapper.builder().build();
	private final Duration timeout = Duration.ofSeconds(10);

	@Test
	void eligeElAdaptadorSegunElProveedor() {
		assertThat(config.pasarelaPagos("simulado", json, "https://api.stripe.com", "", "cop", "pm_card_visa", timeout))
				.isInstanceOf(PasarelaSimulada.class);
		assertThat(config.pasarelaPagos("stripe", json, "https://api.stripe.com", "sk_test_x", "cop", "pm_card_visa",
				timeout)).isInstanceOf(StripeAdapter.class);
	}

	@Test
	void unProveedorDesconocidoDetieneElArranque() {
		assertThatThrownBy(() -> config.pasarelaPagos("paypal", json, "https://api.stripe.com", "", "cop",
				"pm_card_visa", timeout)).isInstanceOf(IllegalStateException.class);
	}
}
