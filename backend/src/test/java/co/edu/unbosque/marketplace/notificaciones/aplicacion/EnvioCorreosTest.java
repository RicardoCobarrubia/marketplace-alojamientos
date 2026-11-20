package co.edu.unbosque.marketplace.notificaciones.aplicacion;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.notificaciones.dominio.CorreoNoEnviadoException;
import co.edu.unbosque.marketplace.notificaciones.dominio.EnvioCorreo;
import co.edu.unbosque.marketplace.notificaciones.dominio.Notificacion;
import co.edu.unbosque.marketplace.notificaciones.dominio.Notificacion.EstadoCorreo;
import co.edu.unbosque.marketplace.notificaciones.infraestructura.NotificacionRepository;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;
import co.edu.unbosque.marketplace.usuarios.infraestructura.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class EnvioCorreosTest {

	@Mock
	private NotificacionRepository notificaciones;
	@Mock
	private UsuarioRepository usuarios;
	@Mock
	private EnvioCorreo correo;

	private EnvioCorreos envio;
	private Notificacion notificacion;

	@BeforeEach
	void preparar() {
		envio = new EnvioCorreos(notificaciones, usuarios, correo, Clock.fixed(AHORA, ZoneOffset.UTC));
		notificacion = new Notificacion(2L, 7L, "RESERVA_CONFIRMADA", "Reserva confirmada", "Tu reserva #50 quedó confirmada.",
				true, AHORA);
	}

	private void pendiente() {
		when(notificaciones.tomarConCorreoPendiente(1L)).thenReturn(Optional.of(notificacion));
		when(usuarios.findById(2L))
				.thenReturn(Optional.of(TestDatos.usuario(2L, Rol.HUESPED, "hash", "huesped@correo.com")));
	}

	@Test
	void enviaElCorreoAlUsuarioYLoMarcaEnviado() {
		pendiente();

		assertThat(envio.enviar(1L)).isTrue();

		verify(correo).enviar(eq("huesped@correo.com"),
				eq("Inversiones LR: Reserva confirmada"),
				contains("Tu reserva #50 quedó confirmada."));
		assertThat(notificacion.getCorreoEstado()).isEqualTo(EstadoCorreo.ENVIADO);
	}

	@Test
	void siElServidorFallaSigueSiendoPendiente() {
		pendiente();
		doThrow(new CorreoNoEnviadoException("caído", null)).when(correo).enviar(any(), any(), any());

		assertThatThrownBy(() -> envio.enviar(1L)).isInstanceOf(CorreoNoEnviadoException.class);
		assertThat(notificacion.getCorreoEstado()).isEqualTo(EstadoCorreo.PENDIENTE);
	}

	@Test
	void siOtraReplicaLaTieneNoEnviaNada() {
		when(notificaciones.tomarConCorreoPendiente(1L)).thenReturn(Optional.empty());

		assertThat(envio.enviar(1L)).isFalse();
		verify(correo, never()).enviar(any(), any(), any());
	}

	@Test
	void registrarUnFalloSumaElIntento() {
		when(notificaciones.tomarConCorreoPendiente(1L)).thenReturn(Optional.of(notificacion));

		envio.registrarFallo(1L);

		assertThat(notificacion.getCorreoIntentos()).isEqualTo(1);
	}

	@Test
	void unFalloNoCambiaUnCorreoQueYaSeEnvio() {
		notificacion.correoEnviado(AHORA);
		when(notificaciones.tomarConCorreoPendiente(1L)).thenReturn(Optional.empty());

		envio.registrarFallo(1L);

		assertThat(notificacion.getCorreoEstado()).isEqualTo(EstadoCorreo.ENVIADO);
		assertThat(notificacion.getCorreoIntentos()).isZero();
	}
}
