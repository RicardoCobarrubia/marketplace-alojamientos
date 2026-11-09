package co.edu.unbosque.marketplace.usuarios.infraestructura.seguridad;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentica cada petición con el token {@code Authorization: Bearer <token>}, igual que en
 * Software 1: extrae el correo, carga el usuario y valida el token.
 * <p>
 * Cambios de Software 2: el usuario sale de la caché de 60 s (ADR-010), se exige que la cuenta
 * siga activa y que la versión de sesión del token sea la actual, y los mensajes van al log
 * en lugar de {@code System.out}.
 * <p>
 * No es un {@code @Component} a propósito: si lo fuera, Spring Boot lo registraría también como
 * filtro del servidor y se ejecutaría dos veces. Lo crea {@link SecurityConfig}.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
	private static final String PREFIJO = "Bearer ";

	private final JwtUtil jwtUtil;
	private final UserDetailsServiceImpl userDetailsService;

	public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService) {
		this.jwtUtil = jwtUtil;
		this.userDetailsService = userDetailsService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		String cabecera = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (cabecera != null && cabecera.startsWith(PREFIJO)
				&& SecurityContextHolder.getContext().getAuthentication() == null) {
			String jwt = cabecera.substring(PREFIJO.length()).trim();
			jwtUtil.extraerUsername(jwt)
					.flatMap(userDetailsService::cargarParaPeticion)
					.filter(usuario -> esValido(jwt, usuario))
					.ifPresent(usuario -> autenticar(usuario, request));
		}
		filterChain.doFilter(request, response);
	}

	private boolean esValido(String jwt, Usuario usuario) {
		boolean valido = usuario.estaActiva() && jwtUtil.validarToken(jwt, usuario);
		if (!valido) {
			log.debug("Token rechazado: sesión revocada o cuenta no activa (usuario {})", usuario.getId());
		}
		return valido;
	}

	private void autenticar(Usuario usuario, HttpServletRequest request) {
		UsernamePasswordAuthenticationToken autenticacion =
				UsernamePasswordAuthenticationToken.authenticated(usuario, null, usuario.getAuthorities());
		autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
		SecurityContextHolder.getContext().setAuthentication(autenticacion);
	}
}
