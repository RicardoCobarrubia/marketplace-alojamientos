package co.edu.unbosque.marketplace.perfiles.dominio;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CodigoRecuperacionTest {

	@Test
	void sirveQuinceMinutosUnaSolaVezYConPocosIntentos() {
		CodigoRecuperacion codigo = new CodigoRecuperacion(3L, "hash", AHORA);

		assertThat(codigo.getVenceEn()).isEqualTo(AHORA.plus(CodigoRecuperacion.VIGENCIA));
		assertThat(codigo.sirve(AHORA.plusSeconds(14 * 60))).isTrue();
		assertThat(codigo.sirve(AHORA.plusSeconds(15 * 60))).isFalse();

		for (int i = 1; i < CodigoRecuperacion.INTENTOS_MAXIMOS; i++) {
			codigo.registrarIntentoFallido();
		}
		assertThat(codigo.sirve(AHORA)).isTrue();
		codigo.registrarIntentoFallido();
		assertThat(codigo.sirve(AHORA)).isFalse();
		assertThat(codigo.getIntentos()).isEqualTo((short) CodigoRecuperacion.INTENTOS_MAXIMOS);
	}

	@Test
	void despuesDeUsarloYaNoSirve() {
		CodigoRecuperacion codigo = new CodigoRecuperacion(3L, "hash", AHORA);

		codigo.usar(AHORA.plusSeconds(30));

		assertThat(codigo.sirve(AHORA.plusSeconds(31))).isFalse();
		assertThat(codigo.getUsadoEn()).isEqualTo(AHORA.plusSeconds(30));
		assertThat(codigo.getUsuarioId()).isEqualTo(3L);
		assertThat(codigo.getCreadoEn()).isEqualTo(AHORA);
	}
}
