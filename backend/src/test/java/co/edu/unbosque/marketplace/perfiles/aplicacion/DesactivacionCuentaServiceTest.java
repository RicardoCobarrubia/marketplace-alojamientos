package co.edu.unbosque.marketplace.perfiles.aplicacion;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.unbosque.marketplace.ServiciosDePrueba;
import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.alojamientos.aplicacion.AlojamientoService;
import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.comun.excepcion.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.comun.excepcion.ReglaNegocioException;
import co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.infraestructura.ReservaRepository;
import co.edu.unbosque.marketplace.usuarios.aplicacion.SesionesEnCache;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.EstadoCuenta;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;
import co.edu.unbosque.marketplace.usuarios.infraestructura.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class DesactivacionCuentaServiceTest {

	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
	private static final LocalDate HOY = LocalDate.ofInstant(AHORA, BOGOTA);
	private static final EnumSet<EstadoReserva> COMPROMETIDAS = EnumSet.of(EstadoReserva.PENDIENTE_PAGO,
			EstadoReserva.CONFIRMADA, EstadoReserva.EN_REVISION);

	@Mock
	private UsuarioRepository usuarioRepository;
	@Mock
	private ReservaRepository reservaRepository;
	@Mock
	private AlojamientoService alojamientoService;
	@Mock
	private SesionesEnCache sesiones;
	@Mock
	private Auditoria auditoria;

	private DesactivacionCuentaService servicio;
	private Usuario anfitrion;

	@BeforeEach
	void preparar() {
		servicio = new DesactivacionCuentaService(ServiciosDePrueba.usuarios(usuarioRepository),
				ServiciosDePrueba.reservas(reservaRepository), alojamientoService, sesiones,
				auditoria, Clock.fixed(AHORA, ZoneOffset.UTC), BOGOTA);
		anfitrion = TestDatos.usuario(1L, Rol.ANFITRION, "hash", "anfitrion@correo.com");
	}

	@Test
	void sinReservasPendientesDesactivaLaCuentaPausaSusAlojamientosYCierraSusSesiones() {
		int version = anfitrion.getVersionSesion();
		when(usuarioRepository.buscarParaActualizar(1L)).thenReturn(Optional.of(anfitrion));
		when(alojamientoService.pausarTodosDelAnfitrion(1L, "Cuenta desactivada por su dueño")).thenReturn(2);
		when(reservaRepository.contarSinTerminarDelUsuario(1L, COMPROMETIDAS, HOY)).thenReturn(0L);

		servicio.desactivar(1L);

		assertThat(anfitrion.getEstadoCuenta()).isEqualTo(EstadoCuenta.DESACTIVADA);
		assertThat(anfitrion.getVersionSesion()).isEqualTo(version + 1);
		verify(sesiones).olvidarAlConfirmar("anfitrion@correo.com");
		verify(auditoria).registrar(EventoAuditoria.cambio("DESACTIVAR_CUENTA", "USUARIO", 1L, EstadoCuenta.ACTIVA,
				EstadoCuenta.DESACTIVADA, "2 alojamientos pausados").conActor(1L, Rol.ANFITRION));
	}

	@Test
	void conReservasConfirmadasSinTerminarSeRechaza() {
		when(usuarioRepository.buscarParaActualizar(1L)).thenReturn(Optional.of(anfitrion));
		when(reservaRepository.contarSinTerminarDelUsuario(1L, COMPROMETIDAS, HOY)).thenReturn(1L);

		assertThatThrownBy(() -> servicio.desactivar(1L)).isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", "RESERVAS_VIGENTES");

		verify(sesiones, never()).olvidarAlConfirmar(any());
		verify(auditoria, never()).registrar(any());
	}

	@Test
	void unAdministradorNoDesactivaSuCuenta() {
		Usuario administrador = TestDatos.usuario(5L, Rol.ADMINISTRADOR, "hash", "admin@correo.com");
		when(usuarioRepository.buscarParaActualizar(5L)).thenReturn(Optional.of(administrador));

		assertThatThrownBy(() -> servicio.desactivar(5L)).isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", "NO_SE_PUEDE_DESACTIVAR");
		assertThat(administrador.getEstadoCuenta()).isEqualTo(EstadoCuenta.ACTIVA);
		verify(alojamientoService, never()).pausarTodosDelAnfitrion(anyLong(), anyString());
	}

	@Test
	void unaCuentaYaDesactivadaNoSeVuelveADesactivar() {
		anfitrion.desactivar();
		when(usuarioRepository.buscarParaActualizar(1L)).thenReturn(Optional.of(anfitrion));

		assertThatThrownBy(() -> servicio.desactivar(1L)).isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", "CUENTA_NO_ACTIVA");
		verify(reservaRepository, never()).contarSinTerminarDelUsuario(eq(1L), any(), any());
	}

	@Test
	void unUsuarioQueNoExisteResponde404() {
		when(usuarioRepository.buscarParaActualizar(9L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.desactivar(9L)).isInstanceOf(RecursoNoEncontradoException.class);
	}
}
