package co.edu.unbosque.marketplace.comun.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.comun.excepcion.AccesoDenegadoException;
import co.edu.unbosque.marketplace.comun.excepcion.ExcepcionMarketplace;

/**
 * Traduce las excepciones a respuestas HTTP con formato Problem Details (RFC 9457).
 * <p>
 * Reemplaza los códigos numéricos que los servicios devolvían en Software 1: el servicio
 * lanza una excepción con significado y aquí se arma la respuesta. Los errores estándar de
 * Spring MVC (JSON mal formado, método no permitido, validación...) los resuelve la clase padre.
 * <p>
 * Toda respuesta lleva el id de correlación para poder rastrear el error en los logs.
 */
@RestControllerAdvice
public class ManejadorGlobalExcepciones extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalExcepciones.class);

	/** Propiedad con el código estable del error, que usa el frontend. */
	private static final String CODIGO = "codigo";

	private final Auditoria auditoria;

	public ManejadorGlobalExcepciones(Auditoria auditoria) {
		this.auditoria = auditoria;
	}

	@ExceptionHandler(ExcepcionMarketplace.class)
	public ProblemDetail manejarExcepcionDeNegocio(ExcepcionMarketplace ex) {
		if (ex instanceof AccesoDenegadoException) {
			auditarAccesoDenegado();
		}
		ProblemDetail problema = crear(ex.getEstado(), ex.getTitulo(), ex.getMessage());
		problema.setProperty(CODIGO, ex.getCodigo());
		ex.getPropiedadesAdicionales().forEach(problema::setProperty);
		return problema;
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail manejarAccesoDenegado(AccessDeniedException ex) {
		auditarAccesoDenegado();
		ProblemDetail problema = crear(HttpStatus.FORBIDDEN, "Acceso denegado",
				"No tienes permiso para realizar esta acción.");
		problema.setProperty(CODIGO, "ACCESO_DENEGADO");
		return problema;
	}

	/** Errores de {@code @Valid}: responde qué campo falló y por qué, para que el frontend lo muestre. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problema = crear(HttpStatus.BAD_REQUEST, "Datos inválidos",
				"Revisa los campos indicados en «errores».");
		problema.setProperty(CODIGO, "DATOS_INVALIDOS");
		Map<String, String> errores = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(error -> errores.putIfAbsent(error.getField(), error.getDefaultMessage()));
		problema.setProperty("errores", errores);
		return ResponseEntity.badRequest().body(problema);
	}

	/** Último recurso: registra el error completo y no expone detalles internos al cliente. */
	@ExceptionHandler(Exception.class)
	public ProblemDetail manejarErrorInesperado(Exception ex) {
		log.error("Error no controlado", ex);
		ProblemDetail problema = crear(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
				"Ocurrió un error inesperado. Reporta el id de correlación para revisarlo.");
		problema.setProperty(CODIGO, "ERROR_INTERNO");
		return problema;
	}

	/**
	 * Los errores estándar de Spring MVC (JSON mal formado, método no permitido...) los arma la
	 * clase padre; aquí se les agrega también el id de correlación.
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> respuesta = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (respuesta != null && respuesta.getBody() instanceof ProblemDetail problema) {
			problema.setProperty("correlationId", MDC.get(CorrelacionFilter.CLAVE_MDC));
		}
		return respuesta;
	}

	/**
	 * Cada acceso denegado queda auditado con quién lo intentó y sobre qué recurso (MA-80 y MA-81,
	 * escenario 3). Si el registro falla, la respuesta 403 se da igual.
	 */
	private void auditarAccesoDenegado() {
		String recurso = "desconocido";
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
			recurso = atributos.getRequest().getMethod() + " " + atributos.getRequest().getRequestURI();
		}
		try {
			auditoria.registrar(EventoAuditoria.accesoDenegado(recurso));
		} catch (RuntimeException e) {
			log.warn("No se pudo auditar un acceso denegado", e);
		}
	}

	private ProblemDetail crear(HttpStatus estado, String titulo, String detalle) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
		problema.setTitle(titulo);
		problema.setProperty("correlationId", MDC.get(CorrelacionFilter.CLAVE_MDC));
		return problema;
	}
}
