package co.edu.unbosque.marketplace.notificaciones.dominio;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.marketplace.notificaciones.dominio.EventoOutbox.EstadoEvento;

class EventoOutboxTest {

	private final EventoOutbox evento = new EventoOutbox("RESERVA_CONFIRMADA", "RESERVA", 50L, "{}", "peticion-1",
			AHORA);

	@Test
	void nacePendienteYAlEntregarseGuardaLaHora() {
		assertThat(evento.getEstado()).isEqualTo(EstadoEvento.PENDIENTE);

		evento.entregar(AHORA.plusSeconds(5));

		assertThat(evento.getEstado()).isEqualTo(EstadoEvento.ENTREGADO);
		assertThat(evento.getEntregadoEn()).isEqualTo(AHORA.plusSeconds(5));
	}

	@Test
	void trasVariosFallosQuedaFallidoConElUltimoError() {
		evento.registrarFallo("primero", 3);
		evento.registrarFallo("segundo", 3);
		assertThat(evento.getEstado()).isEqualTo(EstadoEvento.PENDIENTE);

		evento.registrarFallo("x".repeat(600), 3);

		assertThat(evento.getEstado()).isEqualTo(EstadoEvento.FALLIDO);
		assertThat(evento.getIntentos()).isEqualTo(3);
		assertThat(evento.getUltimoError()).hasSize(500);
	}
}
