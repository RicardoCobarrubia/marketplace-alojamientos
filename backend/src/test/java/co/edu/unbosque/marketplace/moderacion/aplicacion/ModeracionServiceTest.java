package co.edu.unbosque.marketplace.moderacion.aplicacion;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento.EstadoAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.AlojamientoRepository;
import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.comun.configuracion.CacheConfig;
import co.edu.unbosque.marketplace.comun.excepcion.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.comun.excepcion.ReglaNegocioException;
import co.edu.unbosque.marketplace.notificaciones.aplicacion.PublicadorEventos;
import co.edu.unbosque.marketplace.reservas.aplicacion.RevisionReservasService;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.EstadoCuenta;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;
import co.edu.unbosque.marketplace.usuarios.infraestructura.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class ModeracionServiceTest {

	@Mock
	private AlojamientoRepository alojamientoRepository;
	@Mock
	private UsuarioRepository usuarioRepository;
	@Mock
	private RevisionReservasService revisionReservas;
	@Mock
	private Auditoria auditoria;
	@Mock
	private CacheManager cacheManager;
	@Mock
	private Cache cache;
	@Mock
	private PublicadorEventos publicador;

	private ModeracionService servicio;
	private Usuario anfitrion;
	private Alojamiento alojamiento;

	@BeforeEach
	void preparar() {
		servicio = new ModeracionService(alojamientoRepository, usuarioRepository, revisionReservas, auditoria,
				cacheManager, publicador, Clock.fixed(AHORA, ZoneOffset.UTC));
		anfitrion = TestDatos.usuario(1L, Rol.ANFITRION, "hash", "anfitrion@correo.com");
		alojamiento = TestDatos.alojamiento(10L, anfitrion);
		alojamiento.enviarARevision(AHORA);
		alojamiento.cambiarEstado(EstadoAlojamiento.ACTIVA, AHORA);
		TransactionSynchronizationManager.initSynchronization();
	}

	@AfterEach
	void limpiar() {
		TransactionSynchronizationManager.clearSynchronization();
	}

	/** Simula la confirmación de la transacción. */
	private static void confirmar() {
		TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
	}

	@Test
	void bloquearAuditaYPoneEnRevisionLasReservasFuturas() {
		when(alojamientoRepository.buscarParaActualizar(10L)).thenReturn(Optional.of(alojamiento));
		when(revisionReservas.ponerEnRevisionDelAlojamiento(10L, "Alojamiento bloqueado: Fotos falsas")).thenReturn(2);

		ResultadoModeracionDTO resultado = servicio.bloquearAlojamiento(10L, "Fotos falsas");

		assertThat(resultado).isEqualTo(new ResultadoModeracionDTO("ALOJAMIENTO", 10L, "BLOQUEADA", "Fotos falsas", 2, 0));
		verify(auditoria).registrar(EventoAuditoria.cambio("BLOQUEAR_ALOJAMIENTO", "ALOJAMIENTO", 10L, "ACTIVA",
				"BLOQUEADA", "Fotos falsas"));
	}

	@Test
	void desbloquearLoDejaPausada() {
		alojamiento.bloquear("Fotos falsas", AHORA);
		when(alojamientoRepository.buscarParaActualizar(10L)).thenReturn(Optional.of(alojamiento));

		assertThat(servicio.desbloquearAlojamiento(10L).estado()).isEqualTo("PAUSADA");
		verify(auditoria).registrar(EventoAuditoria.cambio("DESBLOQUEAR_ALOJAMIENTO", "ALOJAMIENTO", 10L,
				"BLOQUEADA", "PAUSADA", null));
	}

	@Test
	void unAlojamientoQueNoExisteResponde404() {
		when(alojamientoRepository.buscarParaActualizar(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.bloquearAlojamiento(99L, "Motivo"))
				.isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void restringirPausaSusAlojamientosPoneEnRevisionYOlvidaSuSesionAlConfirmar() {
		when(usuarioRepository.buscarParaActualizar(1L)).thenReturn(Optional.of(anfitrion));
		when(alojamientoRepository.findByAnfitrionIdAndEstado(1L, EstadoAlojamiento.ACTIVA))
				.thenReturn(List.of(alojamiento));
		when(revisionReservas.ponerEnRevisionDelUsuario(1L, "Usuario restringido: Fraude")).thenReturn(3);
		when(cacheManager.getCache(CacheConfig.VERSIONES_SESION)).thenReturn(cache);

		ResultadoModeracionDTO resultado = servicio.restringirUsuario(1L, "Fraude");

		assertThat(resultado).isEqualTo(new ResultadoModeracionDTO("USUARIO", 1L, "RESTRINGIDA", "Fraude", 3, 1));
		assertThat(alojamiento.getEstado()).isEqualTo(EstadoAlojamiento.PAUSADA);
		verify(auditoria).registrar(EventoAuditoria.cambio("RESTRINGIR_USUARIO", "USUARIO", 1L, EstadoCuenta.ACTIVA,
				EstadoCuenta.RESTRINGIDA, "Fraude"));
		verify(auditoria).registrar(EventoAuditoria.cambio("PAUSAR_ALOJAMIENTO", "ALOJAMIENTO", 10L, "ACTIVA",
				"PAUSADA", "Restricción del anfitrión"));
		// La sesión se olvida solo cuando la transacción se confirma
		verify(cache, never()).evict(any());
		confirmar();
		verify(cache).evict("anfitrion@correo.com");
	}

	@Test
	void noSePuedeRestringirAUnAdministrador() {
		when(usuarioRepository.buscarParaActualizar(3L)).thenReturn(Optional.of(TestDatos.usuario(3L, Rol.ADMINISTRADOR, "hash")));

		assertThatThrownBy(() -> servicio.restringirUsuario(3L, "Motivo"))
				.isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", "NO_SE_PUEDE_RESTRINGIR");
		verify(auditoria, never()).registrar(any());
	}

	@Test
	void levantarLaRestriccionReactivaLaCuenta() {
		anfitrion.restringir("Fraude");
		when(usuarioRepository.buscarParaActualizar(1L)).thenReturn(Optional.of(anfitrion));
		when(cacheManager.getCache(CacheConfig.VERSIONES_SESION)).thenReturn(null);

		assertThat(servicio.levantarRestriccion(1L).estado()).isEqualTo("ACTIVA");
		verify(auditoria).registrar(EventoAuditoria.cambio("LEVANTAR_RESTRICCION", "USUARIO", 1L,
				EstadoCuenta.RESTRINGIDA, EstadoCuenta.ACTIVA, null));
		confirmar();
	}

	@Test
	void unUsuarioQueNoExisteResponde404() {
		when(usuarioRepository.buscarParaActualizar(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.levantarRestriccion(99L)).isInstanceOf(RecursoNoEncontradoException.class);
	}
}
