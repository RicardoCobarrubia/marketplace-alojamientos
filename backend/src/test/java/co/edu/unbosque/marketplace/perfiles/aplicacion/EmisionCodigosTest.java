package co.edu.unbosque.marketplace.perfiles.aplicacion;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.edu.unbosque.marketplace.ServiciosDePrueba;
import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria.Resultado;
import co.edu.unbosque.marketplace.perfiles.aplicacion.EmisionCodigos.CodigoEmitido;
import co.edu.unbosque.marketplace.perfiles.dominio.CodigoRecuperacion;
import co.edu.unbosque.marketplace.perfiles.infraestructura.CodigoRecuperacionRepository;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;
import co.edu.unbosque.marketplace.usuarios.infraestructura.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class EmisionCodigosTest {

	private static final String CORREO = "ana@correo.com";

	@Mock
	private UsuarioRepository usuarios;
	@Mock
	private CodigoRecuperacionRepository codigos;
	@Mock
	private Auditoria auditoria;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
	private EmisionCodigos emision;
	private Usuario usuario;

	@BeforeEach
	void preparar() {
		emision = new EmisionCodigos(ServiciosDePrueba.usuarios(usuarios), codigos, passwordEncoder, auditoria,
				Clock.fixed(AHORA, ZoneOffset.UTC));
		usuario = TestDatos.usuario(3L, Rol.HUESPED, "hash");
	}

	private void existe() {
		when(usuarios.findByEmail(CORREO)).thenReturn(Optional.of(usuario));
		when(usuarios.buscarParaActualizar(3L)).thenReturn(Optional.of(usuario));
	}

	@Test
	void creaUnCodigoDeSeisDigitosYGuardaSoloSuHash() {
		existe();

		Optional<CodigoEmitido> emitido = emision.emitir(" Ana@Correo.com ");

		assertThat(emitido).isPresent();
		assertThat(emitido.get().destinatario()).isEqualTo(CORREO);
		assertThat(emitido.get().codigo()).matches("\\d{6}");
		assertThat(emitido.get().toString()).doesNotContain(emitido.get().codigo());
		ArgumentCaptor<CodigoRecuperacion> guardado = ArgumentCaptor.forClass(CodigoRecuperacion.class);
		verify(codigos).save(guardado.capture());
		assertThat(guardado.getValue().getCodigoHash()).doesNotContain(emitido.get().codigo());
		assertThat(passwordEncoder.matches(emitido.get().codigo(), guardado.getValue().getCodigoHash())).isTrue();
		assertThat(guardado.getValue().getVenceEn()).isEqualTo(AHORA.plus(CodigoRecuperacion.VIGENCIA));
		verify(auditoria).registrar(EventoAuditoria.accion("SOLICITAR_RECUPERACION", "USUARIO", 3L,
				Resultado.EXITO, null).conActor(3L, Rol.HUESPED));
	}

	@Test
	void unCorreoQueNoExisteOUnaCuentaRestringidaNoRecibenCodigo() {
		when(usuarios.findByEmail("nadie@correo.com")).thenReturn(Optional.empty());
		assertThat(emision.emitir("nadie@correo.com")).isEmpty();

		usuario.restringir("Fraude");
		existe();
		assertThat(emision.emitir(CORREO)).isEmpty();

		verify(codigos, never()).save(any());
	}

	@Test
	void noCreaOtroCodigoAntesDeUnMinuto() {
		existe();
		when(codigos.findFirstByUsuarioIdOrderByIdDesc(3L))
				.thenReturn(Optional.of(new CodigoRecuperacion(3L, "hash", AHORA.minusSeconds(30))));

		assertThat(emision.emitir(CORREO)).isEmpty();

		verify(codigos, never()).save(any());
		verify(auditoria).registrar(EventoAuditoria.accion("SOLICITAR_RECUPERACION", "USUARIO", 3L,
				Resultado.RECHAZADO, "Límite de códigos").conActor(3L, Rol.HUESPED));
	}

	@Test
	void noCreaMasDeCincoCodigosAlDia() {
		existe();
		when(codigos.findFirstByUsuarioIdOrderByIdDesc(3L))
				.thenReturn(Optional.of(new CodigoRecuperacion(3L, "hash", AHORA.minusSeconds(600))));
		when(codigos.countByUsuarioIdAndCreadoEnAfter(3L, AHORA.minus(Duration.ofDays(1))))
				.thenReturn((long) EmisionCodigos.CODIGOS_POR_DIA);

		assertThat(emision.emitir(CORREO)).isEmpty();

		verify(codigos, never()).save(any());
	}
}
