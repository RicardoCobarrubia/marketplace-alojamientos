package co.edu.unbosque.marketplace.comun.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelacionFilterTest {

	private final CorrelacionFilter filtro = new CorrelacionFilter();

	@Test
	void generaUnIdCuandoLaPeticionNoTraeCabecera() throws Exception {
		MockHttpServletResponse respuesta = ejecutar(new MockHttpServletRequest());

		assertThat(respuesta.getHeader(CorrelacionFilter.CABECERA)).isNotBlank();
	}

	@Test
	void reutilizaElIdQueEnviaElCliente() throws Exception {
		MockHttpServletRequest peticion = new MockHttpServletRequest();
		peticion.addHeader(CorrelacionFilter.CABECERA, "reserva-123");

		MockHttpServletResponse respuesta = ejecutar(peticion);

		assertThat(respuesta.getHeader(CorrelacionFilter.CABECERA)).isEqualTo("reserva-123");
	}

	@Test
	void reemplazaUnIdConCaracteresNoPermitidos() throws Exception {
		MockHttpServletRequest peticion = new MockHttpServletRequest();
		peticion.addHeader(CorrelacionFilter.CABECERA, "abc\nlinea-falsa-en-el-log");

		MockHttpServletResponse respuesta = ejecutar(peticion);

		assertThat(respuesta.getHeader(CorrelacionFilter.CABECERA))
				.isNotEqualTo("abc\nlinea-falsa-en-el-log")
				.doesNotContain("\n");
	}

	@Test
	void dejaElIdEnElMdcDuranteLaPeticionYLoLimpiaAlTerminar() throws Exception {
		MockHttpServletRequest peticion = new MockHttpServletRequest();
		peticion.addHeader(CorrelacionFilter.CABECERA, "id-de-prueba");
		String[] idDuranteLaPeticion = new String[1];

		filtro.doFilter(peticion, new MockHttpServletResponse(),
				(req, res) -> idDuranteLaPeticion[0] = MDC.get(CorrelacionFilter.CLAVE_MDC));

		assertThat(idDuranteLaPeticion[0]).isEqualTo("id-de-prueba");
		assertThat(MDC.get(CorrelacionFilter.CLAVE_MDC)).isNull();
	}

	private MockHttpServletResponse ejecutar(MockHttpServletRequest peticion) throws Exception {
		MockHttpServletResponse respuesta = new MockHttpServletResponse();
		filtro.doFilter(peticion, respuesta, new MockFilterChain());
		return respuesta;
	}
}
