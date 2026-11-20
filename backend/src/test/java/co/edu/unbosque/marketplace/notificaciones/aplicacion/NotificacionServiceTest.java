package co.edu.unbosque.marketplace.notificaciones.aplicacion;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.comun.excepcion.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.notificaciones.dominio.Notificacion;
import co.edu.unbosque.marketplace.notificaciones.infraestructura.NotificacionRepository;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

	@Mock
	private NotificacionRepository notificaciones;

	private NotificacionService servicio;
	private Usuario huesped;

	@BeforeEach
	void preparar() {
		servicio = new NotificacionService(notificaciones, Clock.fixed(AHORA, ZoneOffset.UTC));
		huesped = TestDatos.usuario(2L, Rol.HUESPED, "hash", "huesped@correo.com");
	}

	@Test
	void listaSoloLasDelUsuarioOUnaListaVacia() {
		when(notificaciones.findByUsuarioIdOrderByCreadaEnDescIdDesc(2L))
				.thenReturn(List.of(new Notificacion(2L, 7L, "PAGO_FALLIDO", "Pago no completado", "Mensaje", AHORA)));

		assertThat(servicio.listar(huesped)).singleElement()
				.satisfies(dto -> assertThat(dto.titulo()).isEqualTo("Pago no completado"));
		assertThat(servicio.listar(TestDatos.usuario(9L, Rol.HUESPED, "hash", "otro@correo.com"))).isEmpty();
	}

	@Test
	void marcaComoLeidaSoloUnaPropiaYConservaLaPrimeraLectura() {
		Notificacion propia = new Notificacion(2L, 7L, "PAGO_FALLIDO", "Pago no completado", "Mensaje", AHORA);
		when(notificaciones.findByIdAndUsuarioId(1L, 2L)).thenReturn(Optional.of(propia));
		propia.marcarLeida(AHORA.minusSeconds(60));

		assertThat(servicio.marcarLeida(huesped, 1L).leidaEn()).isEqualTo(AHORA.minusSeconds(60));
		assertThatThrownBy(() -> servicio.marcarLeida(huesped, 2L)).isInstanceOf(RecursoNoEncontradoException.class);
	}
}
