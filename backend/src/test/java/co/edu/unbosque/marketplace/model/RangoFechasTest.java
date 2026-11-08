package co.edu.unbosque.marketplace.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class RangoFechasTest {

	private static RangoFechas rango(int desde, int hasta) {
		return new RangoFechas(LocalDate.of(2026, 12, desde), LocalDate.of(2026, 12, hasta));
	}

	@Test
	void cuentaLasNochesSinIncluirElDiaDeSalida() {
		assertThat(rango(10, 13).noches()).isEqualTo(3);
	}

	@Test
	void unRangoQueTerminaElDiaQueEmpiezaOtroNoSeCruza() {
		assertThat(rango(10, 12).seCruzaCon(rango(12, 14))).isFalse();
		assertThat(rango(10, 13).seCruzaCon(rango(12, 14))).isTrue();
		assertThat(rango(10, 20).seCruzaCon(rango(12, 14))).isTrue();
	}

	@Test
	void contieneSoloSiCabeCompleto() {
		assertThat(rango(1, 31).contiene(rango(10, 12))).isTrue();
		assertThat(rango(10, 12).contiene(rango(10, 12))).isTrue();
		assertThat(rango(10, 12).contiene(rango(11, 13))).isFalse();
	}

	@Test
	void laSalidaDebeSerPosteriorALaLlegada() {
		assertThatThrownBy(() -> rango(12, 12)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> rango(12, 10)).isInstanceOf(IllegalArgumentException.class);
	}
}
