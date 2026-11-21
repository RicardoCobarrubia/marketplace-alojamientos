package co.edu.unbosque.marketplace.perfiles.aplicacion;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.notificaciones.dominio.CorreoNoEnviadoException;
import co.edu.unbosque.marketplace.notificaciones.dominio.EnvioCorreo;
import co.edu.unbosque.marketplace.perfiles.aplicacion.EmisionCodigos.CodigoEmitido;
import co.edu.unbosque.marketplace.perfiles.dominio.CodigoRecuperacion;
import co.edu.unbosque.marketplace.perfiles.infraestructura.CodigoRecuperacionRepository;
import co.edu.unbosque.marketplace.usuarios.aplicacion.SesionesEnCache;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;
import co.edu.unbosque.marketplace.usuarios.infraestructura.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class RecuperacionContrasenaServiceTest {

	private static final String CORREO = "ana@correo.com";

	@Mock
	private EmisionCodigos emision;
	@Mock
	private UsuarioRepository usuarios;
	@Mock
	private CodigoRecuperacionRepository codigos;
	@Mock
	private EnvioCorreo correo;
	@Mock
	private SesionesEnCache sesiones;
	@Mock
	private Auditoria auditoria;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
	private RecuperacionContrasenaService servicio;
	private Usuario usuario;

	@BeforeEach
	void preparar() {
		servicio = new RecuperacionContrasenaService(emision, usuarios, codigos, passwordEncoder, correo, sesiones,
				auditoria, Clock.fixed(AHORA, ZoneOffset.UTC));
		usuario = TestDatos.usuario(3L, Rol.HUESPED, passwordEncoder.encode("clave-vieja-1"));
	}

	@AfterEach
	void limpiar() {
		servicio.detener();
	}

	private void existe() {
		when(usuarios.findByEmail(CORREO)).thenReturn(Optional.of(usuario));
		when(usuarios.buscarParaActualizar(3L)).thenReturn(Optional.of(usuario));
	}

	private CodigoRecuperacion codigoGuardado(String codigo) {
		return new CodigoRecuperacion(3L, passwordEncoder.encode(codigo), AHORA.minusSeconds(120));
	}

	// ─────────────────────────────────────────
	// Solicitar — escenario 1
	// ─────────────────────────────────────────

	@Test
	void creaYEnviaElCodigoFueraDeLaPeticion() {
		when(emision.emitir(CORREO)).thenReturn(Optional.of(new CodigoEmitido(CORREO, "123456")));

		servicio.solicitar(CORREO);

		ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
		verify(correo, timeout(2000)).enviar(eq(CORREO), anyString(), cuerpo.capture());
		assertThat(cuerpo.getValue()).contains("123456").contains("15 minutos");
	}

	@Test
	void sinCodigoNoEnviaNadaYUnCorreoCaidoNoRompeNada() {
		when(emision.emitir("nadie@correo.com")).thenReturn(Optional.empty());
		servicio.solicitar("nadie@correo.com");
		verify(emision, timeout(2000)).emitir("nadie@correo.com");

		when(emision.emitir(CORREO)).thenReturn(Optional.of(new CodigoEmitido(CORREO, "123456")));
		doThrow(new CorreoNoEnviadoException("caído", null)).when(correo).enviar(anyString(), anyString(),
				anyString());
		servicio.solicitar(CORREO);
		verify(correo, timeout(2000)).enviar(eq(CORREO), anyString(), anyString());
	}

	@Test
	void unaRafagaDeSolicitudesNoAcumulaTareasNiAcaparaLaBaseDeDatos() throws Exception {
		CountDownLatch liberar = new CountDownLatch(1);
		CountDownLatch ocupadas = new CountDownLatch(RecuperacionContrasenaService.SOLICITUDES_SIMULTANEAS);
		when(emision.emitir(anyString())).thenAnswer(invocacion -> {
			ocupadas.countDown();
			liberar.await(5, TimeUnit.SECONDS);
			return Optional.empty();
		});

		for (int i = 0; i < RecuperacionContrasenaService.SOLICITUDES_SIMULTANEAS + 3; i++) {
			servicio.solicitar("correo" + i + "@correo.com");
		}
		assertThat(ocupadas.await(5, TimeUnit.SECONDS)).isTrue();
		liberar.countDown();

		verify(emision, timeout(2000).times(RecuperacionContrasenaService.SOLICITUDES_SIMULTANEAS))
				.emitir(anyString());
		// Al terminar se liberan los cupos: una solicitud nueva vuelve a atenderse. Se repite hasta
		// que algún trabajador devuelva su cupo, porque abrir el latch no espera a que terminen
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			servicio.solicitar("otra@correo.com");
			verify(emision, timeout(200).atLeastOnce()).emitir("otra@correo.com");
		});
	}

	// ─────────────────────────────────────────
	// Cambiar la contraseña — escenarios 2 y 3
	// ─────────────────────────────────────────

	@Test
	void conElCodigoCorrectoCambiaLaContrasenaYCierraLasSesiones() {
		existe();
		CodigoRecuperacion codigo = codigoGuardado("123456");
		when(codigos.findFirstByUsuarioIdOrderByIdDesc(3L)).thenReturn(Optional.of(codigo));
		int version = usuario.getVersionSesion();

		servicio.cambiarContrasena(new NuevaContrasenaDTO(CORREO, "123456", "clave-nueva-1"));

		assertThat(passwordEncoder.matches("clave-nueva-1", usuario.getPassword())).isTrue();
		assertThat(passwordEncoder.matches("clave-vieja-1", usuario.getPassword())).isFalse();
		assertThat(usuario.getVersionSesion()).isEqualTo(version + 1);
		assertThat(codigo.getUsadoEn()).isEqualTo(AHORA);
		verify(sesiones).olvidarAlConfirmar(CORREO);
	}

	@Test
	void unCodigoEquivocadoGastaUnIntentoYNoCambiaNada() {
		existe();
		CodigoRecuperacion codigo = codigoGuardado("123456");
		when(codigos.findFirstByUsuarioIdOrderByIdDesc(3L)).thenReturn(Optional.of(codigo));
		String anterior = usuario.getPassword();
		NuevaContrasenaDTO solicitud = new NuevaContrasenaDTO(CORREO, "654321", "clave-nueva-1");

		assertThatThrownBy(() -> servicio.cambiarContrasena(solicitud))
				.isInstanceOf(SolicitudInvalidaException.class)
				.hasFieldOrPropertyWithValue("codigo", "CODIGO_INVALIDO");
		assertThat(codigo.getIntentos()).isEqualTo((short) 1);
		assertThat(usuario.getPassword()).isEqualTo(anterior);
		verify(sesiones, never()).olvidarAlConfirmar(any());
	}

	@Test
	void conLosIntentosAgotadosNiElCodigoCorrectoSirve() {
		existe();
		CodigoRecuperacion codigo = codigoGuardado("123456");
		for (int i = 0; i < CodigoRecuperacion.INTENTOS_MAXIMOS; i++) {
			codigo.registrarIntentoFallido();
		}
		when(codigos.findFirstByUsuarioIdOrderByIdDesc(3L)).thenReturn(Optional.of(codigo));
		String anterior = usuario.getPassword();
		NuevaContrasenaDTO correcta = new NuevaContrasenaDTO(CORREO, "123456", "clave-nueva-1");

		assertThatThrownBy(() -> servicio.cambiarContrasena(correcta))
				.hasFieldOrPropertyWithValue("codigo", "CODIGO_INVALIDO");
		assertThat(usuario.getPassword()).isEqualTo(anterior);
		assertThat(codigo.getUsadoEn()).isNull();
	}

	@Test
	void unCodigoUsadoOSinCuentaSeRechazanIgual() {
		existe();
		CodigoRecuperacion usado = codigoGuardado("123456");
		usado.usar(AHORA.minusSeconds(60));
		when(codigos.findFirstByUsuarioIdOrderByIdDesc(3L)).thenReturn(Optional.of(usado));
		NuevaContrasenaDTO conCuenta = new NuevaContrasenaDTO(CORREO, "123456", "clave-nueva-1");
		when(usuarios.findByEmail("nadie@correo.com")).thenReturn(Optional.empty());
		NuevaContrasenaDTO sinCuenta = new NuevaContrasenaDTO("nadie@correo.com", "123456", "clave-nueva-1");

		assertThatThrownBy(() -> servicio.cambiarContrasena(conCuenta))
				.hasFieldOrPropertyWithValue("codigo", "CODIGO_INVALIDO");
		assertThatThrownBy(() -> servicio.cambiarContrasena(sinCuenta))
				.hasFieldOrPropertyWithValue("codigo", "CODIGO_INVALIDO");
	}

	@Test
	void unaContrasenaDeMasDe72BytesSeRechazaAntesDeRevisarElCodigo() {
		NuevaContrasenaDTO larga = new NuevaContrasenaDTO(CORREO, "123456", "ñ".repeat(40));

		assertThatThrownBy(() -> servicio.cambiarContrasena(larga))
				.hasFieldOrPropertyWithValue("codigo", "CONTRASENA_MUY_LARGA");
		verify(usuarios, never()).findByEmail(any());
		assertThat(larga.toString()).doesNotContain("ñ").doesNotContain("123456");
	}
}
