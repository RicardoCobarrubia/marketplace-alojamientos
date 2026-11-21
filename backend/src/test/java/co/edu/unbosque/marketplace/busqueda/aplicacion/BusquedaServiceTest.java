package co.edu.unbosque.marketplace.busqueda.aplicacion;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.AlojamientoRepository;
import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.cotizacion.aplicacion.CotizacionService;
import co.edu.unbosque.marketplace.disponibilidad.dominio.RangoFechas;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

@ExtendWith(MockitoExtension.class)
class BusquedaServiceTest {

	private static final LocalDate LLEGADA = LocalDate.parse("2026-12-01");
	private static final LocalDate SALIDA = LocalDate.parse("2026-12-04");

	@Mock
	private AlojamientoRepository alojamientos;
	@Mock
	private CotizacionService cotizacion;

	private BusquedaService servicio;

	@BeforeEach
	void preparar() {
		servicio = new BusquedaService(alojamientos, cotizacion, Clock.fixed(AHORA, ZoneOffset.UTC));
	}

	private static CriteriosBusquedaDTO criterios(LocalDate llegada, LocalDate salida, BigDecimal minimo,
			BigDecimal maximo, Integer pagina, Integer tamano) {
		return new CriteriosBusquedaDTO(2L, llegada, salida, 3, (short) 1, minimo, maximo, Set.of((short) 1,
				(short) 2), pagina, tamano);
	}

	@Test
	void devuelveLaPaginaPedidaDelMasBaratoAlMasCaro() {
		Alojamiento cabana = TestDatos.alojamiento(10L, TestDatos.usuario(1L, Rol.ANFITRION, "h"));
		cabana.enviarARevision(AHORA);
		when(cotizacion.validarFechas(LLEGADA, SALIDA)).thenReturn(new RangoFechas(LLEGADA, SALIDA));
		when(alojamientos.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(cabana), PageRequest.of(1, 5), 6));

		ResultadoBusquedaDTO resultado = servicio.buscar(criterios(LLEGADA, SALIDA, BigDecimal.ONE,
				BigDecimal.TEN, 1, 5));

		ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
		verify(alojamientos).findAll(any(Specification.class), pagina.capture());
		assertThat(pagina.getValue().getPageNumber()).isEqualTo(1);
		assertThat(pagina.getValue().getPageSize()).isEqualTo(5);
		assertThat(pagina.getValue().getSort())
				.isEqualTo(Sort.by("precioNoche").ascending().and(Sort.by("id").ascending()));
		assertThat(resultado.alojamientos()).extracting("id").containsExactly(10L);
		assertThat(resultado.total()).isEqualTo(6);
		assertThat(resultado.totalPaginas()).isEqualTo(2);
		assertThat(resultado.mensaje()).isNull();
	}

	@Test
	void sinFiltrosNiResultadosTraeUnMensajeYUsaLaPaginaPorDefecto() {
		when(alojamientos.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

		ResultadoBusquedaDTO resultado = servicio.buscar(new CriteriosBusquedaDTO(null, null, null, null, null, null,
				null, null, null, null));

		assertThat(resultado.alojamientos()).isEmpty();
		assertThat(resultado.pagina()).isZero();
		assertThat(resultado.tamano()).isEqualTo(20);
		assertThat(resultado.mensaje()).contains("No encontramos alojamientos");
		verify(cotizacion, never()).validarFechas(any(), any());
	}

	@Test
	void unRangoDePrecioAlRevesSeRechaza() {
		CriteriosBusquedaDTO alReves = criterios(null, null, new BigDecimal("300000"), new BigDecimal("100000"),
				null, null);

		assertThatThrownBy(() -> servicio.buscar(alReves)).isInstanceOf(SolicitudInvalidaException.class)
				.hasFieldOrPropertyWithValue("codigo", "RANGO_PRECIO_INVALIDO");
		verify(alojamientos, never()).findAll(any(Specification.class), any(Pageable.class));
	}

	@Test
	void conUnaSolaFechaSeValidanComoAlCotizar() {
		when(cotizacion.validarFechas(eq(LLEGADA), eq(null)))
				.thenThrow(new SolicitudInvalidaException("FECHAS_INVALIDAS", "Indica la fecha de llegada y la de salida."));
		CriteriosBusquedaDTO soloLlegada = criterios(LLEGADA, null, null, null, null, null);

		assertThatThrownBy(() -> servicio.buscar(soloLlegada)).isInstanceOf(SolicitudInvalidaException.class)
				.hasFieldOrPropertyWithValue("codigo", "FECHAS_INVALIDAS");
	}
}
