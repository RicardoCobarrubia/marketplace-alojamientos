package co.edu.unbosque.marketplace.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.ServletWebRequest;

import co.edu.unbosque.marketplace.filter.CorrelacionFilter;

class ManejadorGlobalExcepcionesTest {

	private final ManejadorGlobalExcepciones manejador = new ManejadorGlobalExcepciones();

	@BeforeEach
	void ponerIdDeCorrelacion() {
		MDC.put(CorrelacionFilter.CLAVE_MDC, "peticion-1");
	}

	@AfterEach
	void limpiar() {
		MDC.clear();
	}

	@Test
	void recursoNoEncontradoResponde404() {
		ProblemDetail problema = manejador.manejarRecursoNoEncontrado(
				new RecursoNoEncontradoException("No existe el alojamiento 7."));

		assertThat(problema.getStatus()).isEqualTo(404);
		assertThat(problema.getTitle()).isEqualTo("Recurso no encontrado");
		assertThat(problema.getDetail()).isEqualTo("No existe el alojamiento 7.");
		assertThat(problema.getProperties()).containsEntry("correlationId", "peticion-1");
	}

	@Test
	void reglaDeNegocioResponde409() {
		ProblemDetail problema = manejador.manejarReglaNegocio(
				new ReglaNegocioException("Las fechas ya están ocupadas."));

		assertThat(problema.getStatus()).isEqualTo(409);
		assertThat(problema.getDetail()).isEqualTo("Las fechas ya están ocupadas.");
	}

	@Test
	void accesoDenegadoResponde403() {
		ProblemDetail problema = manejador.manejarAccesoDenegado(new AccessDeniedException("denegado"));

		assertThat(problema.getStatus()).isEqualTo(403);
	}

	@Test
	void errorInesperadoNoExponeElMensajeInterno() {
		ProblemDetail problema = manejador.manejarErrorInesperado(new IllegalStateException("detalle interno"));

		assertThat(problema.getStatus()).isEqualTo(500);
		assertThat(problema.getDetail()).doesNotContain("detalle interno");
		assertThat(problema.getProperties()).containsEntry("correlationId", "peticion-1");
	}

	@Test
	void losErroresEstandarDeSpringTambienLlevanElIdDeCorrelacion() throws Exception {
		HttpMessageNotReadableException jsonMalFormado = new HttpMessageNotReadableException("JSON mal formado",
				new MockHttpInputMessage(new byte[0]));

		ResponseEntity<Object> respuesta = manejador.handleException(jsonMalFormado,
				new ServletWebRequest(new MockHttpServletRequest()));

		assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
		assertThat(respuesta.getBody()).isInstanceOfSatisfying(ProblemDetail.class, problema ->
				assertThat(problema.getProperties()).containsEntry("correlationId", "peticion-1"));
	}
}
