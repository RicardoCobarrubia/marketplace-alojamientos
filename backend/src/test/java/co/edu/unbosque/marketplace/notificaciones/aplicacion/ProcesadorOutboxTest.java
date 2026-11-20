package co.edu.unbosque.marketplace.notificaciones.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.unbosque.marketplace.notificaciones.infraestructura.EventoOutboxRepository;

@ExtendWith(MockitoExtension.class)
class ProcesadorOutboxTest {

	@Mock
	private EventoOutboxRepository eventos;
	@Mock
	private EntregaEventos entrega;

	@Test
	void unEventoQueFallaNoDetieneALosDemasYQuedaRegistrado() {
		when(eventos.idsPendientes(anyInt())).thenReturn(List.of(1L, 2L, 3L));
		when(entrega.entregar(1L)).thenReturn(true);
		when(entrega.entregar(2L)).thenThrow(new IllegalArgumentException("sin huespedId"));
		when(entrega.entregar(3L)).thenReturn(false);

		assertThat(new ProcesadorOutbox(eventos, entrega).procesarPendientes()).isEqualTo(1);

		verify(entrega).registrarFallo(2L, "IllegalArgumentException: sin huespedId");
		verify(entrega, never()).registrarFallo(eq(3L), any());
	}
}
