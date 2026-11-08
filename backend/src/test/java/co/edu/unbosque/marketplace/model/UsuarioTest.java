package co.edu.unbosque.marketplace.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.model.Usuario.EstadoCuenta;
import co.edu.unbosque.marketplace.model.Usuario.Rol;

class UsuarioTest {

	private static final Instant AHORA = Instant.parse("2026-11-07T12:00:00Z");

	@Test
	void alCrearloNormalizaElCorreoYQuedaActivo() {
		Usuario usuario = new Usuario(" Ana ", "  Ana@Correo.COM ", "300 123 4567", "hash", Rol.HUESPED, AHORA);

		assertThat(usuario.getEmail()).isEqualTo("ana@correo.com");
		assertThat(usuario.getUsername()).isEqualTo("ana@correo.com");
		assertThat(usuario.getNombre()).isEqualTo("Ana");
		assertThat(usuario.estaActiva()).isTrue();
		assertThat(usuario.getVersionSesion()).isZero();
	}

	@Test
	void suAutoridadEsElRolConPrefijoRole() {
		Usuario usuario = TestDatos.usuario(1L, Rol.ANFITRION, "hash");

		assertThat(usuario.getAuthorities()).extracting(GrantedAuthority::getAuthority)
				.containsExactly("ROLE_ANFITRION");
	}

	@Test
	void elEstadoDeLaCuentaSeReflejaEnUserDetails() {
		Usuario usuario = TestDatos.usuario(1L, Rol.HUESPED, "hash");
		assertThat(usuario.isEnabled()).isTrue();
		assertThat(usuario.isAccountNonLocked()).isTrue();

		TestDatos.asignar(usuario, "estadoCuenta", EstadoCuenta.DESACTIVADA);
		assertThat(usuario.isEnabled()).isFalse();

		TestDatos.asignar(usuario, "estadoCuenta", EstadoCuenta.RESTRINGIDA);
		assertThat(usuario.isAccountNonLocked()).isFalse();
	}

	@Test
	void seBloqueaAlLlegarAlMaximoDeIntentosYSeDesbloqueaAlPasarElTiempo() {
		Usuario usuario = TestDatos.usuario(1L, Rol.HUESPED, "hash");

		for (int i = 0; i < 4; i++) {
			usuario.registrarIntentoFallido(AHORA, 5, Duration.ofMinutes(15));
		}
		assertThat(usuario.estaBloqueadoTemporalmente(AHORA)).isFalse();

		usuario.registrarIntentoFallido(AHORA, 5, Duration.ofMinutes(15));

		assertThat(usuario.estaBloqueadoTemporalmente(AHORA.plusSeconds(60))).isTrue();
		assertThat(usuario.estaBloqueadoTemporalmente(AHORA.plus(Duration.ofMinutes(15)))).isFalse();
	}

	@Test
	void elInicioExitosoReiniciaLosIntentos() {
		Usuario usuario = TestDatos.usuario(1L, Rol.HUESPED, "hash");
		usuario.registrarIntentoFallido(AHORA, 5, Duration.ofMinutes(15));

		usuario.registrarInicioSesionExitoso();

		assertThat(usuario.getIntentosFallidos()).isZero();
		assertThat(usuario.getBloqueadoHasta()).isNull();
	}

	@Test
	void toStringNoMuestraElHash() {
		assertThat(TestDatos.usuario(1L, Rol.HUESPED, "hash-secreto").toString()).doesNotContain("hash-secreto");
	}
}
