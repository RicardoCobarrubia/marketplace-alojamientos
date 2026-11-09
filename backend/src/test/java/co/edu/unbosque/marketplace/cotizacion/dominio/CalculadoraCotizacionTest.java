package co.edu.unbosque.marketplace.cotizacion.dominio;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.ServicioAdicional;
import co.edu.unbosque.marketplace.alojamientos.dominio.ServicioAdicional.Modalidad;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

class CalculadoraCotizacionTest {

	private static final ConfiguracionCotizacion CONFIGURACION = new ConfiguracionCotizacion(
			new BigDecimal("10.00"), new BigDecimal("10.00"), 7);

	private final CalculadoraCotizacion calculadora = new CalculadoraCotizacion(List.of(new DescuentoEstadiaLarga()));

	private Alojamiento alojamiento;
	private ServicioAdicional desayuno;
	private ServicioAdicional traslado;

	@BeforeEach
	void preparar() {
		// Precio por noche 250.000 y limpieza 40.000
		alojamiento = TestDatos.alojamiento(10L, TestDatos.usuario(1L, Rol.ANFITRION, "hash"));
		desayuno = alojamiento.agregarServicioAdicional("Desayuno", new BigDecimal("30000"), Modalidad.POR_NOCHE, AHORA);
		TestDatos.asignar(desayuno, "id", 4L);
		traslado = alojamiento.agregarServicioAdicional("Traslado", new BigDecimal("50000"), Modalidad.POR_ESTADIA,
				AHORA);
		TestDatos.asignar(traslado, "id", 5L);
	}

	@Test
	void sinDescuentoNiServiciosSumaLimpiezaYTarifa() {
		Cotizacion cotizacion = calculadora.calcular(alojamiento, 6, List.of(), CONFIGURACION);

		assertThat(cotizacion.noches()).isEqualTo(6);
		assertThat(cotizacion.subtotal()).isEqualByComparingTo("1500000");
		assertThat(cotizacion.descuentos()).isEmpty();
		assertThat(cotizacion.limpieza()).isEqualByComparingTo("40000");
		// 10 % sobre 1.540.000
		assertThat(cotizacion.tarifaServicio()).isEqualByComparingTo("154000");
		assertThat(cotizacion.total()).isEqualByComparingTo("1694000");
	}

	@Test
	void conElMinimoDeNochesAplicaElDescuentoYCobraLosServiciosSegunSuUnidad() {
		Cotizacion cotizacion = calculadora.calcular(alojamiento, 7, List.of(traslado, desayuno), CONFIGURACION);

		assertThat(cotizacion.subtotal()).isEqualByComparingTo("1750000");
		assertThat(cotizacion.descuentos()).singleElement()
				.satisfies(linea -> assertThat(linea.valor()).isEqualByComparingTo("175000"));
		// Desayuno por noche: 30.000 × 7; traslado por estadía: 50.000 una vez
		assertThat(cotizacion.servicios()).extracting(Cotizacion.LineaServicio::cantidad).containsExactly(7L, 1L);
		assertThat(cotizacion.totalServicios()).isEqualByComparingTo("260000");
		// 1.750.000 - 175.000 + 40.000 + 260.000 = 1.875.000, más el 10 %
		assertThat(cotizacion.tarifaServicio()).isEqualByComparingTo("187500");
		assertThat(cotizacion.total()).isEqualByComparingTo("2062500");
	}

	@Test
	void laMismaEntradaDaSiempreElMismoTotal() {
		Cotizacion primera = calculadora.calcular(alojamiento, 9, List.of(desayuno, traslado), CONFIGURACION);
		Cotizacion segunda = calculadora.calcular(alojamiento, 9, List.of(traslado, desayuno), CONFIGURACION);

		assertThat(segunda).isEqualTo(primera);
	}

	@Test
	void unDescuentoNuevoSeAgregaSinCambiarLaCalculadora() {
		ReglaDescuento temporadaBaja = new ReglaDescuento() {
			@Override
			public String nombre() {
				return "Temporada baja";
			}

			@Override
			public BigDecimal calcular(long noches, BigDecimal subtotal, ConfiguracionCotizacion configuracion) {
				return new BigDecimal("100000");
			}
		};
		CalculadoraCotizacion conDosReglas = new CalculadoraCotizacion(
				List.of(new DescuentoEstadiaLarga(), temporadaBaja));

		Cotizacion cotizacion = conDosReglas.calcular(alojamiento, 7, List.of(), CONFIGURACION);

		assertThat(cotizacion.descuentos()).hasSize(2);
		assertThat(cotizacion.totalDescuentos()).isEqualByComparingTo("275000");
	}

	@Test
	void losDescuentosNuncaDejanElSubtotalEnNegativo() {
		ReglaDescuento excesivo = new ReglaDescuento() {
			@Override
			public String nombre() {
				return "Excesivo";
			}

			@Override
			public BigDecimal calcular(long noches, BigDecimal subtotal, ConfiguracionCotizacion configuracion) {
				return subtotal.multiply(BigDecimal.TWO);
			}
		};

		Cotizacion cotizacion = new CalculadoraCotizacion(List.of(excesivo))
				.calcular(alojamiento, 2, List.of(), CONFIGURACION);

		assertThat(cotizacion.totalDescuentos()).isEqualByComparingTo(cotizacion.subtotal());
		assertThat(cotizacion.total()).isEqualByComparingTo("44000");
	}

	@Test
	void rechazaUnaEstadiaSinNoches() {
		List<ServicioAdicional> sinServicios = List.of();

		assertThatThrownBy(() -> calculadora.calcular(alojamiento, 0, sinServicios, CONFIGURACION))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
