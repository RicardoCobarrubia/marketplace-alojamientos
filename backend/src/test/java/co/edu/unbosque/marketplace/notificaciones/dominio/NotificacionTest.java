package co.edu.unbosque.marketplace.notificaciones.dominio;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.marketplace.notificaciones.dominio.Notificacion.EstadoCorreo;

class NotificacionTest {

	@Test
	void conCorreoQuedaPendienteHastaEnviarse() {
		Notificacion notificacion = new Notificacion(2L, 7L, "PAGO_FALLIDO", "Título", "Mensaje", true, AHORA);
		assertThat(notificacion.getCorreoEstado()).isEqualTo(EstadoCorreo.PENDIENTE);

		notificacion.correoEnviado(AHORA.plusSeconds(3));

		assertThat(notificacion.getCorreoEstado()).isEqualTo(EstadoCorreo.ENVIADO);
		assertThat(notificacion.getCorreoEnviadoEn()).isEqualTo(AHORA.plusSeconds(3));
	}

	@Test
	void sinCorreoQuedaOmitido() {
		assertThat(new Notificacion(2L, 7L, "PAGO_FALLIDO", "Título", "Mensaje", false, AHORA).getCorreoEstado())
				.isEqualTo(EstadoCorreo.OMITIDO);
	}

	@Test
	void trasVariosFallosElCorreoQuedaFallido() {
		Notificacion notificacion = new Notificacion(2L, 7L, "PAGO_FALLIDO", "Título", "Mensaje", true, AHORA);

		notificacion.correoFallido(2);
		assertThat(notificacion.getCorreoEstado()).isEqualTo(EstadoCorreo.PENDIENTE);
		notificacion.correoFallido(2);

		assertThat(notificacion.getCorreoEstado()).isEqualTo(EstadoCorreo.FALLIDO);
		assertThat(notificacion.getCorreoIntentos()).isEqualTo(2);
	}

	@Test
	void laPrimeraLecturaNoCambia() {
		Notificacion notificacion = new Notificacion(2L, 7L, "PAGO_FALLIDO", "Título", "Mensaje", false, AHORA);

		notificacion.marcarLeida(AHORA);
		notificacion.marcarLeida(AHORA.plusSeconds(60));

		assertThat(notificacion.getLeidaEn()).isEqualTo(AHORA);
	}
}
