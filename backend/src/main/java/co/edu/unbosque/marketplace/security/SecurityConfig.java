package co.edu.unbosque.marketplace.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuración de seguridad, con la misma estructura de Software 1: reglas por ruta con
 * {@code hasRole}, filtro JWT antes del de usuario y contraseña, {@code DaoAuthenticationProvider}
 * con BCrypt y {@code AuthenticationManager} para el login.
 * <p>
 * Cambios de Software 2: sin sesión en el servidor para que las réplicas sean sin estado
 * (ADR-008), CORS solo para el frontend, respuestas 401/403 en JSON y el estado de la cuenta
 * se revisa después de la contraseña para no revelarlo a quien no la conoce.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private final JwtUtil jwtUtil;
	private final UserDetailsServiceImpl userDetailsService;

	public SecurityConfig(JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService) {
		this.jwtUtil = jwtUtil;
		this.userDetailsService = userDetailsService;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			CorsConfigurationSource corsConfigurationSource) {

		RespuestasSeguridad respuestas = new RespuestasSeguridad();

		http
			.cors(cors -> cors.configurationSource(corsConfigurationSource))
			// CSRF solo aplica cuando el navegador envía la sesión sola (cookies). Aquí no hay
			// cookies de sesión: el token viaja en el encabezado Authorization y lo pone el
			// frontend a mano, así que una página ajena no puede usarlo.
			.csrf(AbstractHttpConfigurer::disable) // NOSONAR: API sin estado con JWT en encabezado
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(errores -> errores
				.authenticationEntryPoint(respuestas)
				.accessDeniedHandler(respuestas))
			.authorizeHttpRequests(auth -> auth
				// Públicas
				.requestMatchers("/api/auth/**").permitAll()
				.requestMatchers("/actuator/health/**").permitAll()
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

				// Administración — MA-49
				.requestMatchers("/api/admin/**").hasRole("ADMINISTRADOR")

				// El resto exige sesión
				.anyRequest().authenticated())
			.authenticationProvider(authenticationProvider())
			.addFilterBefore(new JwtAuthenticationFilter(jwtUtil, userDetailsService),
				UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	public AuthenticationProvider authenticationProvider() {
		DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider(userDetailsService);
		proveedor.setPasswordEncoder(passwordEncoder());
		// Por defecto Spring revisa si la cuenta está bloqueada o desactivada ANTES de la
		// contraseña, y eso le diría a cualquiera el estado de la cuenta. Aquí se revisa DESPUÉS.
		proveedor.setPreAuthenticationChecks(usuario -> {
		});
		proveedor.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
		return proveedor;
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration configuracion) {
		return configuracion.getAuthenticationManager();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
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
}
