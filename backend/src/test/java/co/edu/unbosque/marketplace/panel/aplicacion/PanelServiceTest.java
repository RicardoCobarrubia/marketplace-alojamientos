package co.edu.unbosque.marketplace.panel.aplicacion;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.panel.infraestructura.ConsultasPanelRepository;
import co.edu.unbosque.marketplace.panel.infraestructura.ConsultasPanelRepository.Conteo;
import co.edu.unbosque.marketplace.panel.infraestructura.ConsultasPanelRepository.Ingresos;
import co.edu.unbosque.marketplace.panel.infraestructura.ConsultasPanelRepository.ReservasPorCiudad;

@ExtendWith(MockitoExtension.class)
class PanelServiceTest {

	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

	record ConteoDePrueba(String getClave, long getTotal) implements Conteo {
	}

	record IngresosDePrueba(BigDecimal getIngresos, long getReservas) implements Ingresos {
	}

	record CiudadDePrueba(Long getCiudadId, String getCiudad, String getDepartamento, long getReservas)
			implements ReservasPorCiudad {
	}

	@Mock
	private ConsultasPanelRepository consultas;

	private PanelService servicio;

	@BeforeEach
	void preparar() {
		// AHORA es el 7 de noviembre a las 7:00 en Bogotá
		servicio = new PanelService(consultas, Clock.fixed(AHORA, ZoneOffset.UTC), BOGOTA);
	}

	private static Instant inicioDelDia(String fecha) {
		return LocalDate.parse(fecha).atStartOfDay(BOGOTA).toInstant();
	}

	@Test
	void losIndicadoresTraenTodosLosRolesYEstadosConSusTotales() {
		when(consultas.usuariosPorRol()).thenReturn(List.of(new ConteoDePrueba("HUESPED", 7),
				new ConteoDePrueba("ANFITRION", 2)));
		when(consultas.reservasPorEstado()).thenReturn(List.of(new ConteoDePrueba("CONFIRMADA", 4),
				new ConteoDePrueba("CANCELADA", 1)));
		when(consultas.alojamientosActivos()).thenReturn(3L);

		IndicadoresDTO indicadores = servicio.indicadores();

		assertThat(indicadores.usuarios()).isEqualTo(9);
		assertThat(indicadores.usuariosPorRol()).containsExactly(Map.entry("HUESPED", 7L),
				Map.entry("ANFITRION", 2L), Map.entry("ADMINISTRADOR", 0L));
		assertThat(indicadores.alojamientosActivos()).isEqualTo(3);
		assertThat(indicadores.reservas()).isEqualTo(5);
		assertThat(indicadores.reservasPorEstado()).hasSize(6)
				.containsEntry("CONFIRMADA", 4L).containsEntry("CANCELADA", 1L).containsEntry("PENDIENTE_PAGO", 0L);
		assertThat(indicadores.generadoEn()).isEqualTo(AHORA);
	}

	@Test
	void sinFechasUsaLosUltimos30DiasEnLaHoraDeColombia() {
		Instant desde = inicioDelDia("2026-10-09");
		Instant hasta = inicioDelDia("2026-11-08");
		when(consultas.ingresos(desde, hasta)).thenReturn(new IngresosDePrueba(new BigDecimal("45000.00"), 3));
		when(consultas.ciudadesConMasReservas(desde, hasta))
				.thenReturn(List.of(new CiudadDePrueba(2L, "Medellín", "Antioquia", 3)));

		IngresosDTO ingresos = servicio.ingresos(null, null);

		assertThat(ingresos.desde()).isEqualTo(LocalDate.parse("2026-10-09"));
		assertThat(ingresos.hasta()).isEqualTo(LocalDate.parse("2026-11-07"));
		assertThat(ingresos.ingresosEstimados()).isEqualByComparingTo("45000");
		assertThat(ingresos.reservas()).isEqualTo(3);
		assertThat(ingresos.ciudadesConMasReservas())
				.containsExactly(new IngresosDTO.CiudadReservas(2L, "Medellín", "Antioquia", 3));
	}

	@Test
	void conUnaSolaFechaElRangoEsDe30DiasDesdeOHastaElla() {
		when(consultas.ingresos(inicioDelDia("2026-01-01"), inicioDelDia("2026-01-31")))
				.thenReturn(new IngresosDePrueba(BigDecimal.ZERO, 0));
		when(consultas.ingresos(inicioDelDia("2026-01-02"), inicioDelDia("2026-02-01")))
				.thenReturn(new IngresosDePrueba(BigDecimal.ZERO, 0));

		assertThat(servicio.ingresos(LocalDate.parse("2026-01-01"), null).hasta())
				.isEqualTo(LocalDate.parse("2026-01-30"));
		assertThat(servicio.ingresos(null, LocalDate.parse("2026-01-31")).desde())
				.isEqualTo(LocalDate.parse("2026-01-02"));
		verify(consultas).ciudadesConMasReservas(inicioDelDia("2026-01-01"), inicioDelDia("2026-01-31"));
	}

	@Test
	void rechazaUnRangoAlReves() {
		LocalDate desde = LocalDate.parse("2026-02-01");
		LocalDate hasta = LocalDate.parse("2026-01-01");

		assertThatThrownBy(() -> servicio.ingresos(desde, hasta)).isInstanceOf(SolicitudInvalidaException.class)
				.hasFieldOrPropertyWithValue("codigo", "RANGO_INVALIDO");
	}
}
