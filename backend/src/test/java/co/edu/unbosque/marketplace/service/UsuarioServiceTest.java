package co.edu.unbosque.marketplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.dto.UsuarioDTO;
import co.edu.unbosque.marketplace.exception.CredencialesInvalidasException;
import co.edu.unbosque.marketplace.exception.CuentaBloqueadaTemporalmenteException;
import co.edu.unbosque.marketplace.exception.CuentaDesactivadaException;
import co.edu.unbosque.marketplace.exception.CuentaRestringidaException;
import co.edu.unbosque.marketplace.exception.ReglaNegocioException;
import co.edu.unbosque.marketplace.exception.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.model.Usuario;
import co.edu.unbosque.marketplace.model.Usuario.Rol;
import co.edu.unbosque.marketplace.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

	private static final Instant AHORA = Instant.parse("2026-11-07T12:00:00Z");
	private static final UsuarioDTO LOGIN = new UsuarioDTO(null, null, "Ana@Correo.com", null, "clave-correcta",
			null, null);

	@Mock
	private UsuarioRepository usuarioRepository;
	@Mock
	private AuthenticationManager authenticationManager;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
	private UsuarioService servicio;
	private Usuario usuario;

	@BeforeEach
	void preparar() {
		servicio = new UsuarioService(usuarioRepository, passwordEncoder, authenticationManager,
				Clock.fixed(AHORA, ZoneOffset.UTC), 5, Duration.ofMinutes(15));
		usuario = TestDatos.usuario(3L, Rol.HUESPED, "hash-real");
	}

	// ─────────────────────────────────────────
	// Registro — MA-47
	// ─────────────────────────────────────────

	private static UsuarioDTO registro(Rol rol) {
		return new UsuarioDTO(null, "Ana Pérez", "Ana@Correo.com", "3001234567", "clave-segura-1", rol, null);
	}

	@Test
	void registraConElRolElegidoYGuardaSoloElHashDeLaContrasena() {
		when(usuarioRepository.existsByEmail("ana@correo.com")).thenReturn(false);
		when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

		UsuarioDTO respuesta = servicio.registrar(registro(Rol.ANFITRION));

		ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
		verify(usuarioRepository).saveAndFlush(guardado.capture());
		assertThat(guardado.getValue().getPassword()).isNotEqualTo("clave-segura-1").startsWith("$2");
		assertThat(passwordEncoder.matches("clave-segura-1", guardado.getValue().getPassword())).isTrue();
		assertThat(respuesta.email()).isEqualTo("ana@correo.com");
		assertThat(respuesta.rol()).isEqualTo(Rol.ANFITRION);
		assertThat(respuesta.password()).isNull();
	}

	@Test
	void rechazaElRolAdministrador() {
		assertThatThrownBy(() -> servicio.registrar(registro(Rol.ADMINISTRADOR)))
				.isInstanceOf(SolicitudInvalidaException.class)
				.hasFieldOrPropertyWithValue("codigo", "ROL_NO_PERMITIDO");
		verify(usuarioRepository, never()).saveAndFlush(any());
	}

	@Test
	void rechazaUnCorreoYaRegistrado() {
		when(usuarioRepository.existsByEmail("ana@correo.com")).thenReturn(true);

		assertThatThrownBy(() -> servicio.registrar(registro(Rol.HUESPED)))
				.isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", UsuarioService.CORREO_DUPLICADO);
	}

	@Test
	void siDosRegistrosLleganALaVezElSegundoRecibeCorreoDuplicado() {
		when(usuarioRepository.existsByEmail("ana@correo.com")).thenReturn(false);
		when(usuarioRepository.saveAndFlush(any(Usuario.class)))
				.thenThrow(new DataIntegrityViolationException("email duplicado"));

		assertThatThrownBy(() -> servicio.registrar(registro(Rol.HUESPED)))
				.isInstanceOf(ReglaNegocioException.class);
	}

	// ─────────────────────────────────────────
	// Inicio de sesión — MA-48
	// ─────────────────────────────────────────

	@Test
	void conCredencialesValidasDevuelveElUsuarioYReiniciaLosIntentos() {
		usuario.registrarIntentoFallido(AHORA, 5, Duration.ofMinutes(15));
		when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(usuario));
		when(authenticationManager.authenticate(any()))
				.thenReturn(UsernamePasswordAuthenticationToken.authenticated(usuario, null, usuario.getAuthorities()));

		Usuario autenticado = servicio.iniciarSesion(LOGIN);

		assertThat(autenticado).isSameAs(usuario);
		assertThat(autenticado.getIntentosFallidos()).isZero();
	}

	@Test
	void conCorreoInexistenteDaElErrorGenerico() {
		when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.empty());
		when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("x"));

		assertThatThrownBy(() -> servicio.iniciarSesion(LOGIN))
				.isInstanceOf(CredencialesInvalidasException.class);
	}

	@Test
	void conContrasenaIncorrectaSumaUnIntentoFallidoYDespuesDeCincoSeBloquea() {
		when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(usuario));
		when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("x"));

		assertThatThrownBy(() -> servicio.iniciarSesion(LOGIN))
				.isInstanceOf(CredencialesInvalidasException.class);
		assertThat(usuario.getIntentosFallidos()).isEqualTo((short) 1);

		for (int i = 0; i < 4; i++) {
			assertThatThrownBy(() -> servicio.iniciarSesion(LOGIN))
					.isInstanceOf(CredencialesInvalidasException.class);
		}
		assertThatThrownBy(() -> servicio.iniciarSesion(LOGIN))
				.isInstanceOf(CuentaBloqueadaTemporalmenteException.class);
	}

	@Test
	void unaCuentaRestringidaNoEntra() {
		when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(usuario));
		when(authenticationManager.authenticate(any())).thenThrow(new LockedException("x"));

		assertThatThrownBy(() -> servicio.iniciarSesion(LOGIN))
				.isInstanceOf(CuentaRestringidaException.class);
	}

	@Test
	void unaCuentaDesactivadaRecibeElCodigoParaReactivarse() {
		when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(usuario));
		when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("x"));

		assertThatThrownBy(() -> servicio.iniciarSesion(LOGIN))
				.isInstanceOf(CuentaDesactivadaException.class)
				.hasFieldOrPropertyWithValue("codigo", "CUENTA_DESACTIVADA");
	}
}
