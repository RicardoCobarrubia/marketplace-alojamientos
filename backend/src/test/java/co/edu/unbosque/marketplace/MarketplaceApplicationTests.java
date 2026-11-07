package co.edu.unbosque.marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifica que la aplicación arranca completa contra un PostgreSQL real
 * levantado por Testcontainers (requiere Docker en ejecución).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class MarketplaceApplicationTests {

	@Test
	void contextLoads() {
	}

}
