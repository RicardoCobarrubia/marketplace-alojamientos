package co.edu.unbosque.marketplace.notificaciones.aplicacion;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.notificaciones.dominio.EventoOutbox;
import co.edu.unbosque.marketplace.notificaciones.dominio.EventoOutbox.EstadoEvento;
import co.edu.unbosque.marketplace.notificaciones.dominio.Notificacion;
import co.edu.unbosque.marketplace.notificaciones.infraestructura.EventoOutboxRepository;
import co.edu.unbosque.marketplace.notificaciones.infraestructura.NotificacionRepository;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.EstadoCuenta;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;
import co.edu.unbosque.marketplace.usuarios.infraestructura.UsuarioRepository;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EntregaEventosTest {

	@Mock
	private EventoOutboxRepository eventos;
	@Mock
	private NotificacionRepository notificaciones;
	@Mock
	private UsuarioRepository usuarios;

	private EntregaEventos entrega;

	@BeforeEach
	void preparar() {
		entrega = new EntregaEventos(eventos, notificaciones, usuarios, new ReglasNotificacion(),
				JsonMapper.builder().build(), Clock.fixed(AHORA, ZoneOffset.UTC), true);
	}

	private static EventoOutbox evento(String tipo, String payload) {
		EventoOutbox evento = new EventoOutbox(tipo, "RESERVA", 50L, payload, null, AHORA);
		TestDatos.asignar(evento, "id", 7L);
		return evento;
	}

	@Test
	void generaUnaNotificacionPorPersonaYMarcaElEventoEntregado() {
		EventoOutbox confirmada = evento("RESERVA_CONFIRMADA",
				"{\"reservaId\":50,\"huespedId\":2,\"anfitrionId\":1,\"llegada\":\"2026-12-20\",\"salida\":\"2026-12-23\"}");
		when(eventos.tomarPendiente(7L)).thenReturn(Optional.of(confirmada));
		// Si una entrega anterior alcanzó a crear la del anfitrión, no se repite
		when(notificaciones.existsByEventoIdAndUsuarioId(7L, 2L)).thenReturn(false);
		when(notificaciones.existsByEventoIdAndUsuarioId(7L, 1L)).thenReturn(true);

		assertThat(entrega.entregar(7L)).isTrue();

		ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);
		verify(notificaciones).save(guardada.capture());
		assertThat(guardada.getValue().getUsuarioId()).isEqualTo(2L);
		assertThat(guardada.getValue().getEventoId()).isEqualTo(7L);
		assertThat(guardada.getValue().getCorreoEstado()).isEqualTo(Notificacion.EstadoCorreo.PENDIENTE);
		assertThat(confirmada.getEstado()).isEqualTo(EstadoEvento.ENTREGADO);
	}

	@Test
	void losAdministradoresActivosRecibenLasReservasEnRevision() {
		when(eventos.tomarPendiente(7L)).thenReturn(Optional.of(evento("RESERVA_EN_REVISION",
				"{\"reservaId\":50,\"huespedId\":2,\"anfitrionId\":1,\"motivo\":\"Bloqueo\"}")));
		when(usuarios.findByRolAndEstadoCuenta(Rol.ADMINISTRADOR, EstadoCuenta.ACTIVA))
				.thenReturn(List.of(TestDatos.usuario(3L, Rol.ADMINISTRADOR, "hash")));

		entrega.entregar(7L);

		verify(notificaciones, times(3)).save(any());
	}

	@Test
	void siOtraReplicaLoTieneONoEstaPendienteNoHaceNada() {
		when(eventos.tomarPendiente(7L)).thenReturn(Optional.empty());

		assertThat(entrega.entregar(7L)).isFalse();
		verify(notificaciones, never()).save(any());
	}

	@Test
	void registrarUnFalloSumaElIntento() {
		EventoOutbox pendiente = evento("PAGO_FALLIDO", "{}");
		when(eventos.findById(7L)).thenReturn(Optional.of(pendiente));

		entrega.registrarFallo(7L, "error");

		assertThat(pendiente.getIntentos()).isEqualTo(1);
		assertThat(pendiente.getUltimoError()).isEqualTo("error");
	}
}
