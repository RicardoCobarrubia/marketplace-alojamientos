package co.edu.unbosque.marketplace.comun.configuracion;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Documentación OpenAPI (Swagger) de la API REST.
 * En desarrollo se consulta en {@code /swagger-ui.html}; en producción está apagada.
 */
@Configuration
public class OpenApiConfig {

	private static final String ESQUEMA_JWT = "bearerAuth";

	@Bean
	public OpenAPI marketplaceOpenApi() {
		Info info = new Info()
				.title("Marketplace de Alojamientos · Inversiones LR — API REST")
				.version("1.0")
				.description("Para probar rutas protegidas: inicia sesión, copia el token, "
						+ "pulsa Authorize y pégalo.");

		SecurityScheme esquemaJwt = new SecurityScheme()
				.type(SecurityScheme.Type.HTTP)
				.scheme("bearer")
				.bearerFormat("JWT");

		return new OpenAPI()
				.info(info)
				.addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT))
				.components(new Components().addSecuritySchemes(ESQUEMA_JWT, esquemaJwt));
	}
}
