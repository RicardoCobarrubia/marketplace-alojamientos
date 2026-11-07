package co.edu.unbosque.marketplace.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import co.edu.unbosque.marketplace.filter.CorrelacionFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Respuestas 401 y 403 que da Spring Security antes de llegar a un controlador (MA-49,
 * escenario 1). Usan el mismo formato Problem Details que {@code ManejadorGlobalExcepciones}.
 */
public class RespuestasSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

	/** Sin token, token inválido, vencido o revocado. */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		escribir(response, HttpStatus.UNAUTHORIZED, "No autenticado", "NO_AUTENTICADO",
				"Debes iniciar sesión para realizar esta acción.");
	}

	/** Autenticado, pero su rol no tiene permiso. */
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		escribir(response, HttpStatus.FORBIDDEN, "Acceso denegado", "ACCESO_DENEGADO",
				"No tienes permiso para realizar esta acción.");
	}

	private void escribir(HttpServletResponse response, HttpStatus estado, String titulo, String codigo,
			String detalle) throws IOException {
		// El id de correlación solo tiene letras, números y guiones (lo valida CorrelacionFilter),
		// y los demás textos son fijos, así que se puede armar el JSON sin escapar.
		String idCorrelacion = MDC.get(CorrelacionFilter.CLAVE_MDC);
		String json = "{\"type\":\"about:blank\""
				+ ",\"title\":\"" + titulo + "\""
				+ ",\"status\":" + estado.value()
				+ ",\"detail\":\"" + detalle + "\""
				+ ",\"codigo\":\"" + codigo + "\""
				+ ",\"correlationId\":" + (idCorrelacion == null ? "null" : "\"" + idCorrelacion + "\"")
				+ "}";
		response.setStatus(estado.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(json);
	}
}
