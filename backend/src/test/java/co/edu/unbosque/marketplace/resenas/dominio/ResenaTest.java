package co.edu.unbosque.marketplace.resenas.dominio;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import co.edu.unbosque.marketplace.comun.excepcion.ReglaNegocioException;
import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;

class ResenaTest {

	@Test
	void guardaLaReservaElAlojamientoElHuespedLaFechaYElContenido() {
		Resena resena = new Resena(50L, 10L, 2L, 5, "  Excelente estadía, todo limpio.  ", AHORA);

		assertThat(resena.getReservaId()).isEqualTo(50L);
		assertThat(resena.getAlojamientoId()).isEqualTo(10L);
		assertThat(resena.getHuespedId()).isEqualTo(2L);
		assertThat(resena.getCalificacion()).isEqualTo(5);
		assertThat(resena.getComentario()).isEqualTo("Excelente estadía, todo limpio.");
		assertThat(resena.getCreadaEn()).isEqualTo(AHORA);
		assertThat(resena.isOculta()).isFalse();
		assertThat(resena.visibilidad()).isEqualTo("VISIBLE");
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 6, -1 })
	void laCalificacionVaDeUnoACinco(int calificacion) {
		assertThatThrownBy(() -> new Resena(50L, 10L, 2L, calificacion, "Comentario válido", AHORA))
				.isInstanceOf(SolicitudInvalidaException.class)
				.hasFieldOrPropertyWithValue("codigo", "CALIFICACION_INVALIDA");
	}

	@Test
	void elComentarioTieneEntreDiezYMilCaracteres() {
		String corto = "   Corto    ";
		String largo = "x".repeat(1001);

		assertThatThrownBy(() -> new Resena(50L, 10L, 2L, 4, corto, AHORA))
				.hasFieldOrPropertyWithValue("codigo", "COMENTARIO_INVALIDO");
		assertThatThrownBy(() -> new Resena(50L, 10L, 2L, 4, largo, AHORA))
				.hasFieldOrPropertyWithValue("codigo", "COMENTARIO_INVALIDO");
		assertThatThrownBy(() -> new Resena(50L, 10L, 2L, 4, null, AHORA))
				.hasFieldOrPropertyWithValue("codigo", "COMENTARIO_INVALIDO");
		assertThat(new Resena(50L, 10L, 2L, 4, "x".repeat(1000), AHORA).getComentario()).hasSize(1000);
	}

	@Test
	void elLargoDelComentarioCuentaCadaEmojiComoUnCaracterIgualQuePostgresql() {
		String emoji = "\uD83D\uDE00";

		assertThatThrownBy(() -> new Resena(50L, 10L, 2L, 4, emoji.repeat(5), AHORA))
				.hasFieldOrPropertyWithValue("codigo", "COMENTARIO_INVALIDO");
		assertThatThrownBy(() -> new Resena(50L, 10L, 2L, 4, emoji.repeat(1001), AHORA))
				.hasFieldOrPropertyWithValue("codigo", "COMENTARIO_INVALIDO");
		assertThat(new Resena(50L, 10L, 2L, 4, emoji.repeat(10), AHORA).getComentario()).isEqualTo(emoji.repeat(10));
		String mixto = "x".repeat(900) + emoji.repeat(60);
		assertThat(new Resena(50L, 10L, 2L, 4, mixto, AHORA).getComentario()).isEqualTo(mixto);
	}

	@Test
	void elAdministradorLaOcultaConMotivoYLaVuelveAMostrar() {
		Resena resena = new Resena(50L, 10L, 2L, 1, "Comentario ofensivo de prueba", AHORA);

		resena.ocultar(" Lenguaje ofensivo ", AHORA.plusSeconds(10));
		assertThat(resena.isOculta()).isTrue();
		assertThat(resena.visibilidad()).isEqualTo("OCULTA");
		assertThat(resena.getMotivoOcultamiento()).isEqualTo("Lenguaje ofensivo");
		assertThat(resena.getModeradaEn()).isEqualTo(AHORA.plusSeconds(10));
		assertThatThrownBy(() -> resena.ocultar("Otra vez", AHORA)).isInstanceOf(ReglaNegocioException.class);

		resena.mostrar(AHORA.plusSeconds(20));
		assertThat(resena.isOculta()).isFalse();
		assertThat(resena.getMotivoOcultamiento()).isNull();
		assertThatThrownBy(() -> resena.mostrar(AHORA)).isInstanceOf(ReglaNegocioException.class);
	}
}
