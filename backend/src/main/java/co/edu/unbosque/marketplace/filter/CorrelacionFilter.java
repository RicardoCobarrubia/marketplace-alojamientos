package co.edu.unbosque.marketplace.filter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Asigna un id de correlación a cada petición (patrón Correlation Identifier, ADR-012).
 * <p>
 * Si la petición ya trae la cabecera {@value #CABECERA} con un valor válido, lo reutiliza;
 * si no, genera uno nuevo. El id queda en el MDC para que aparezca en todos los logs de la
 * petición y se devuelve en la respuesta para que el cliente pueda reportarlo.
 * <p>
 * Se ejecuta antes que Spring Security, así los logs de autenticación también llevan el id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelacionFilter extends OncePerRequestFilter {

	public static final String CABECERA = "X-Correlation-Id";
	public static final String CLAVE_MDC = "correlationId";

	/** Solo letras, números y guiones: evita que un cliente inyecte texto en los logs. */
	private static final Pattern FORMATO_VALIDO = Pattern.compile("[A-Za-z0-9-]{1,64}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		String idCorrelacion = obtenerOGenerar(request.getHeader(CABECERA));
		MDC.put(CLAVE_MDC, idCorrelacion);
		response.setHeader(CABECERA, idCorrelacion);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(CLAVE_MDC);
		}
	}

	private String obtenerOGenerar(String recibido) {
		if (recibido != null && FORMATO_VALIDO.matcher(recibido).matches()) {
			return recibido;
		}
		return UUID.randomUUID().toString();
	}
}
