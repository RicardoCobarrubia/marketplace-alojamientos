package co.edu.unbosque.marketplace.comun.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import co.edu.unbosque.marketplace.TestcontainersConfiguration;

/**
 * Lo que usan Traefik y Prometheus en el despliegue (ADR-008): el estado de la réplica y sus
 * métricas, sin token. El resto de /actuator sigue cerrado. Requiere Docker en ejecución.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ObservabilidadIntegrationTest {

	@Autowired
	private WebApplicationContext contexto;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
	}

	@Test
	void traefikYPrometheusLeenElEstadoYLasMetricasSinToken() throws Exception {
		MockMvc mvc = mvc();
		mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
				.andExpect(content().string(containsString("UP")));
		mvc.perform(get("/api/catalogos/ciudades")).andExpect(status().isOk());

		mvc.perform(get("/actuator/prometheus"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("jvm_memory_used_bytes")))
				.andExpect(content().string(containsString("hikaricp_connections_active")))
				.andExpect(content().string(containsString("application=\"marketplace\"")));
	}

	@Test
	void elRestoDeActuatorSigueCerrado() throws Exception {
		mvc().perform(get("/actuator/env")).andExpect(status().isUnauthorized());
	}
}
