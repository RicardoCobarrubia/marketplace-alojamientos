package co.edu.unbosque.marketplace.reservas.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CalculadoraDevolucionTest {

	private static final Map<String, Object> FLEXIBLE = Map.of("horasAntesParaReembolsoTotal", 48,
			"porcentajeReembolsoDespues", 50);
	private static final BigDecimal TOTAL = new BigDecimal("693000.00");

	private final CalculadoraDevolucion calculadora = new CalculadoraDevolucion(List.of(new DevolucionFlexible()));

	@Test
	void flexibleDevuelveTodoConCuarentaYOchoHorasOMasYLaMitadConMenos() {
		assertThat(calculadora.calcular("FLEXIBLE", FLEXIBLE, TOTAL, Duration.ofHours(48), false))
				.isEqualByComparingTo("693000");
		assertThat(calculadora.calcular("FLEXIBLE", FLEXIBLE, TOTAL, Duration.ofHours(47).plusMinutes(59), false))
				.isEqualByComparingTo("346500");
		assertThat(calculadora.calcular("FLEXIBLE", FLEXIBLE, new BigDecimal("100.01"), Duration.ZERO, false))
				.isEqualTo(new BigDecimal("50.01"));
	}

	@Test
	void elAdministradorSiempreDevuelveElTotal() {
		assertThat(calculadora.calcular("OTRA", Map.of(), TOTAL, Duration.ZERO, true)).isEqualTo(TOTAL);
	}

	@Test
	void unaPoliticaNuevaSoloNecesitaSuRegla() {
		ReglaDevolucion estricta = new ReglaDevolucion() {
			@Override
			public String codigo() {
				return "ESTRICTA";
			}

			@Override
			public BigDecimal porcentaje(Map<String, Object> parametros, Duration anticipacion) {
				return BigDecimal.ZERO;
			}
		};
		CalculadoraDevolucion conDos = new CalculadoraDevolucion(List.of(new DevolucionFlexible(), estricta));

		assertThat(conDos.calcular("ESTRICTA", Map.of(), TOTAL, Duration.ofDays(30), false)).isZero();
		assertThat(conDos.calcular("FLEXIBLE", FLEXIBLE, TOTAL, Duration.ofDays(30), false)).isEqualTo(TOTAL);
	}

	@Test
	void rechazaPoliticasDesconocidasParametrosFaltantesYPorcentajesFueraDeRango() {
		Duration unDia = Duration.ofDays(1);
		Map<String, Object> incompleta = Map.of("horasAntesParaReembolsoTotal", 48);
		Map<String, Object> excesiva = Map.of("horasAntesParaReembolsoTotal", 48, "porcentajeReembolsoDespues", 150);
		Map<String, Object> sinNumero = Map.of("horasAntesParaReembolsoTotal", "48");

		assertThatThrownBy(() -> calculadora.calcular("OTRA", FLEXIBLE, TOTAL, unDia, false))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> calculadora.calcular("FLEXIBLE", incompleta, TOTAL, unDia, false))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> calculadora.calcular("FLEXIBLE", excesiva, TOTAL, unDia, false))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> calculadora.calcular("FLEXIBLE", sinNumero, TOTAL, unDia, false))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void dosReglasConElMismoCodigoSonUnErrorDeConfiguracion() {
		List<ReglaDevolucion> repetidas = List.of(new DevolucionFlexible(), new DevolucionFlexible());

		assertThatThrownBy(() -> new CalculadoraDevolucion(repetidas)).isInstanceOf(IllegalStateException.class);
	}
}
