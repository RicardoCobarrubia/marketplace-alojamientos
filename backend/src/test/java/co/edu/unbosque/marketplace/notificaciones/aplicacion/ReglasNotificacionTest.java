package co.edu.unbosque.marketplace.notificaciones.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ReglasNotificacionTest {

	private static final String RESERVA = """
			{"reservaId":50,"huespedId":2,"anfitrionId":1,"llegada":"2026-12-20","salida":"2026-12-23",
			 "valorDevolucion":693000.00,"motivo":"Fotos engañosas"}""";
	private static final String ALOJAMIENTO = """
			{"alojamientoId":10,"anfitrionId":1,"titulo":"Casa en la playa","motivo":"Faltan fotos"}""";

	private final ReglasNotificacion reglas = new ReglasNotificacion();
	private final AtomicInteger consultasDeAdministradores = new AtomicInteger();
	private final Supplier<List<Long>> administradores = () -> {
		consultasDeAdministradores.incrementAndGet();
		return List.of(3L, 4L);
	};

	private static JsonNode datos(String json) {
		return JsonMapper.builder().build().readTree(json);
	}

	private List<Aviso> avisos(String tipo, String json) {
		return reglas.avisos(tipo, datos(json), administradores);
	}

	@Test
	void unaReservaConfirmadaOCanceladaSeNotificaAlHuespedYAlAnfitrion() {
		assertThat(avisos("RESERVA_CONFIRMADA", RESERVA)).extracting(Aviso::usuarioId).containsExactly(2L, 1L);
		assertThat(avisos("RESERVA_CONFIRMADA", RESERVA).get(0).mensaje())
				.isEqualTo("Tu reserva #50 del 2026-12-20 al 2026-12-23 quedó confirmada.");
		List<Aviso> cancelada = avisos("RESERVA_CANCELADA", RESERVA);
		assertThat(cancelada).extracting(Aviso::usuarioId).containsExactly(2L, 1L);
		assertThat(cancelada.get(0).mensaje()).contains("$693.000");
		assertThat(avisos("RESERVA_MANTENIDA", RESERVA)).extracting(Aviso::usuarioId).containsExactly(2L, 1L);
		assertThat(consultasDeAdministradores).hasValue(0);
	}

	@Test
	void unaReservaEnRevisionTambienSeNotificaALosAdministradores() {
		List<Aviso> enRevision = avisos("RESERVA_EN_REVISION", RESERVA);

		assertThat(enRevision).extracting(Aviso::usuarioId).containsExactly(2L, 1L, 3L, 4L);
		assertThat(enRevision.get(2).titulo()).isEqualTo("Reserva por revisar");
		assertThat(enRevision.get(2).mensaje()).contains("Fotos engañosas");
		assertThat(consultasDeAdministradores).hasValue(1);
	}

	@Test
	void elPagoFallidoYLaDevolucionSoloSeNotificanAlHuesped() {
		assertThat(avisos("PAGO_FALLIDO", RESERVA)).extracting(Aviso::usuarioId).containsExactly(2L);
		assertThat(avisos("DEVOLUCION_SOLICITADA", RESERVA)).singleElement()
				.satisfies(aviso -> assertThat(aviso.mensaje()).contains("$693.000").contains("#50"));
	}

	@Test
	void losCambiosDelAlojamientoSeNotificanAlAnfitrion() {
		assertThat(avisos("ALOJAMIENTO_APROBADO", ALOJAMIENTO)).singleElement()
				.satisfies(aviso -> assertThat(aviso.mensaje()).isEqualTo("«Casa en la playa» fue aprobado y ya está publicado."));
		assertThat(avisos("ALOJAMIENTO_RECHAZADO", ALOJAMIENTO)).singleElement()
				.satisfies(aviso -> assertThat(aviso.mensaje()).contains("Faltan fotos"));
		assertThat(avisos("ALOJAMIENTO_BLOQUEADO", ALOJAMIENTO)).extracting(Aviso::usuarioId).containsExactly(1L);
	}

	@Test
	void unTipoSinReglaNoNotificaANadie() {
		assertThat(avisos("RESERVA_COMPLETADA", RESERVA)).isEmpty();
	}

	@Test
	void unEventoSinLosDatosNecesariosEsUnError() {
		JsonNode incompleto = datos("{\"reservaId\":50}");

		assertThatThrownBy(() -> reglas.avisos("PAGO_FALLIDO", incompleto, administradores))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void losPesosVanSinDecimalesConPuntoDeMiles() {
		assertThat(ReglasNotificacion.pesos(new BigDecimal("1234567.50"))).isEqualTo("$1.234.568");
	}

	@Test
	void lasResenasSeNotificanAlAnfitrionYSuModeracionAlHuesped() {
		String resena = """
				{"resenaId":7,"huespedId":2,"anfitrionId":1,"titulo":"Casa en la playa","calificacion":4,
				 "motivo":"Lenguaje ofensivo"}""";

		assertThat(avisos("RESENA_PUBLICADA", resena)).singleElement()
				.satisfies(aviso -> {
					assertThat(aviso.usuarioId()).isEqualTo(1L);
					assertThat(aviso.mensaje()).isEqualTo("«Casa en la playa» recibió una reseña de 4 estrellas.");
				});
		assertThat(avisos("RESENA_OCULTA", resena)).singleElement()
				.satisfies(aviso -> {
					assertThat(aviso.usuarioId()).isEqualTo(2L);
					assertThat(aviso.mensaje()).contains("Lenguaje ofensivo");
				});
		assertThat(avisos("RESENA_VISIBLE", resena)).extracting(Aviso::usuarioId).containsExactly(2L);
	}
}
