package co.edu.unbosque.marketplace.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.model.Usuario;
import co.edu.unbosque.marketplace.model.Usuario.Rol;

class JwtUtilTest {

	private static final String SECRETO = "secreto-de-prueba-con-mas-de-32-caracteres";
	private static final Instant AHORA = Instant.parse("2026-11-07T12:00:00Z");

	private final Usuario usuario = TestDatos.usuario(7L, Rol.ANFITRION, "hash");

	private JwtUtil jwtUtilEn(Instant instante) {
		return new JwtUtil(SECRETO, Duration.ofHours(2), Clock.fixed(instante, ZoneOffset.UTC));
	}

	@Test
	void elTokenLlevaElCorreoYEsValidoParaSuUsuario() {
		JwtUtil jwtUtil = jwtUtilEn(AHORA);

		String token = jwtUtil.generarToken(usuario);

		assertThat(jwtUtil.extraerUsername(token)).contains("ana@correo.com");
		assertThat(jwtUtil.extraerExpiracion(token)).contains(AHORA.plus(Duration.ofHours(2)));
		assertThat(jwtUtil.validarToken(token, usuario)).isTrue();
	}

	@Test
	void unTokenVencidoNoEsValido() {
		String token = jwtUtilEn(AHORA).generarToken(usuario);
		JwtUtil despues = jwtUtilEn(AHORA.plus(Duration.ofHours(3)));

		assertThat(despues.extraerUsername(token)).isEmpty();
		assertThat(despues.validarToken(token, usuario)).isFalse();
	}

	@Test
	void unTokenDeOtroUsuarioNoEsValido() {
		JwtUtil jwtUtil = jwtUtilEn(AHORA);
		String token = jwtUtil.generarToken(usuario);
		Usuario otro = new Usuario("Luis", "luis@correo.com", "3000000000", "hash", Rol.HUESPED, AHORA);

		assertThat(jwtUtil.validarToken(token, otro)).isFalse();
	}

	@Test
	void siCambiaLaVersionDeSesionElTokenDejaDeServir() {
		JwtUtil jwtUtil = jwtUtilEn(AHORA);
		String token = jwtUtil.generarToken(usuario);

		TestDatos.asignar(usuario, "versionSesion", 1);

		assertThat(jwtUtil.validarToken(token, usuario)).isFalse();
	}

	@Test
	void unTokenAlteradoOFirmadoConOtroSecretoNoEsValido() {
		String token = jwtUtilEn(AHORA).generarToken(usuario);
		String alterado = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
		JwtUtil otroSecreto = new JwtUtil("otro-secreto-distinto-con-mas-de-32-caracteres",
				Duration.ofHours(2), Clock.fixed(AHORA, ZoneOffset.UTC));

		assertThat(jwtUtilEn(AHORA).extraerUsername(alterado)).isEmpty();
		assertThat(jwtUtilEn(AHORA).extraerUsername("no-es-un-jwt")).isEmpty();
		assertThat(otroSecreto.extraerUsername(token)).isEmpty();
	}

	@Test
	void rechazaUnSecretoCorto() {
		assertThatThrownBy(() -> new JwtUtil("corto", Duration.ofHours(2), Clock.systemUTC()))
				.isInstanceOf(IllegalStateException.class);
	}
}
