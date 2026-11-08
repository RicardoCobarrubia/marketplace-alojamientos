package co.edu.unbosque.marketplace.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuración base de seguridad.
 * <p>
 * La API no guarda sesión en el servidor: cada réplica valida el token por su cuenta, por
 * eso las réplicas pueden ser sin estado (ADR-008). El filtro JWT, el ServicioDeTokens y las
 * reglas por rol se agregan con la historia MA-48/MA-49; por ahora solo son públicas las
 * rutas de salud, documentación y autenticación, y el resto exige estar autenticado.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private static final String[] RUTAS_PUBLICAS = {
			"/actuator/health/**",
			"/v3/api-docs/**",
			"/swagger-ui/**",
			"/swagger-ui.html",
			"/api/auth/**"
	};

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			CorsConfigurationSource corsConfigurationSource) {
		http
			.cors(cors -> cors.configurationSource(corsConfigurationSource))
			// CSRF solo aplica cuando el navegador envía la sesión sola (cookies). Aquí no hay
			// cookies de sesión: el token viaja en el encabezado Authorization y lo pone el
			// frontend a mano, así que una página ajena no puede usarlo.
			.csrf(AbstractHttpConfigurer::disable) // NOSONAR: API sin estado con JWT en encabezado
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(RUTAS_PUBLICAS).permitAll()
				.anyRequest().authenticated());
		return http.build();
	}

	/**
	 * Solo acepta peticiones del frontend configurado en {@code marketplace.cors.origenes-permitidos}.
	 * En Software 1 se permitía cualquier origen ({@code *}).
	 */
	@Bean
	public CorsConfigurationSource corsConfigurationSource(
			@Value("${marketplace.cors.origenes-permitidos}") List<String> origenesPermitidos) {
		CorsConfiguration configuracion = new CorsConfiguration();
		configuracion.setAllowedOrigins(origenesPermitidos);
		configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuracion.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id"));
		configuracion.setExposedHeaders(List.of("X-Correlation-Id"));

		UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
		fuente.registerCorsConfiguration("/**", configuracion);
		return fuente;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
