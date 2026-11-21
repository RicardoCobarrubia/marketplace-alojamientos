package co.edu.unbosque.marketplace.usuarios.infraestructura;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

@ExtendWith(MockitoExtension.class)
class CargaDatosDesarrolloTest {

	@Mock
	private UsuarioRepository usuarios;

	private CommandLineRunner cargador(String email, String password) {
		return new CargaDatosDesarrollo().crearAdministradorInicial(usuarios, new BCryptPasswordEncoder(4),
				Clock.fixed(AHORA, ZoneOffset.UTC), email, password);
	}

	@Test
	void creaElAdministradorInicialConLaContrasenaCifrada() throws Exception {
		when(usuarios.existsByEmail("admin@correo.com")).thenReturn(false);

		cargador(" Admin@Correo.com ", "clave-segura-1").run();

		ArgumentCaptor<Usuario> creado = ArgumentCaptor.forClass(Usuario.class);
		verify(usuarios).saveAndFlush(creado.capture());
		assertThat(creado.getValue().getEmail()).isEqualTo("admin@correo.com");
		assertThat(creado.getValue().getRol()).isEqualTo(Rol.ADMINISTRADOR);
		assertThat(creado.getValue().getPassword()).startsWith("$2").doesNotContain("clave-segura-1");
	}

	@Test
	void sinCorreoOContrasenaOSiYaExisteNoCreaNada() throws Exception {
		cargador("", "clave-segura-1").run();
		cargador("admin@correo.com", " ").run();
		when(usuarios.existsByEmail("admin@correo.com")).thenReturn(true);
		cargador("admin@correo.com", "clave-segura-1").run();

		verify(usuarios, never()).saveAndFlush(any());
	}

	@Test
	void siOtraReplicaLoCreoALaVezSigueArrancando() {
		when(usuarios.existsByEmail("admin@correo.com")).thenReturn(false, true);
		when(usuarios.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("correo duplicado"));

		assertThatCode(() -> cargador("admin@correo.com", "clave-segura-1").run()).doesNotThrowAnyException();
	}

	@Test
	void otroErrorDeLaBaseDeDatosDetieneElArranque() {
		when(usuarios.existsByEmail("admin@correo.com")).thenReturn(false, false);
		when(usuarios.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("valor muy largo"));

		assertThatThrownBy(() -> cargador("admin@correo.com", "clave-segura-1").run())
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
