package co.edu.unbosque.marketplace.usuarios.dominio;

import co.edu.unbosque.marketplace.comun.excepcion.ReglaNegocioException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.EstadoCuenta;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

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

	// ─────────────────────────────────────────
	// Moderación — MA-81
	// ─────────────────────────────────────────

	@Test
	void alRestringirloNoPuedeEntrarYSusTokensDejanDeValer() {
		Usuario usuario = TestDatos.usuario(1L, Rol.ANFITRION, "hash");
		int version = usuario.getVersionSesion();

		usuario.restringir(" Fraude ");

		assertThat(usuario.getEstadoCuenta()).isEqualTo(EstadoCuenta.RESTRINGIDA);
		assertThat(usuario.getMotivoRestriccion()).isEqualTo("Fraude");
		assertThat(usuario.getVersionSesion()).isEqualTo(version + 1);
		assertThat(usuario.isAccountNonLocked()).isFalse();
		assertThat(usuario.estaActiva()).isFalse();
		assertThatThrownBy(() -> usuario.restringir("Otra vez")).isInstanceOf(ReglaNegocioException.class);

		usuario.levantarRestriccion();
		assertThat(usuario.getEstadoCuenta()).isEqualTo(EstadoCuenta.ACTIVA);
		assertThat(usuario.getMotivoRestriccion()).isNull();
		assertThatThrownBy(usuario::levantarRestriccion).isInstanceOf(ReglaNegocioException.class);
	}

	// ─────────────────────────────────────────
	// Perfil y estado de la cuenta — MA-51, MA-52
	// ─────────────────────────────────────────

	@Test
	void elPerfilSoloCambiaNombreYTelefono() {
		Usuario usuario = TestDatos.usuario(1L, Rol.HUESPED, "hash");

		usuario.actualizarPerfil("  Ana María  ", " 3109876543 ");

		assertThat(usuario.getNombre()).isEqualTo("Ana María");
		assertThat(usuario.getTelefono()).isEqualTo("3109876543");
		assertThat(usuario.getEmail()).isEqualTo("ana@correo.com");
		assertThat(usuario.getRol()).isEqualTo(Rol.HUESPED);
	}

	@Test
	void alDesactivarlaSusTokensDejanDeValerYSoloSePuedeReactivar() {
		Usuario usuario = TestDatos.usuario(1L, Rol.ANFITRION, "hash");
		int version = usuario.getVersionSesion();
		assertThatThrownBy(usuario::reactivar).isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", "CUENTA_NO_DESACTIVADA");

		usuario.desactivar();

		assertThat(usuario.getEstadoCuenta()).isEqualTo(EstadoCuenta.DESACTIVADA);
		assertThat(usuario.getVersionSesion()).isEqualTo(version + 1);
		assertThat(usuario.isEnabled()).isFalse();
		assertThat(usuario.estaActiva()).isFalse();
		assertThatThrownBy(usuario::desactivar).isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", "CUENTA_NO_ACTIVA");

		usuario.reactivar();
		assertThat(usuario.getEstadoCuenta()).isEqualTo(EstadoCuenta.ACTIVA);
		assertThat(usuario.getVersionSesion()).isEqualTo(version + 1);
	}

	@Test
	void siEstabaDesactivadaAlLevantarLaRestriccionSigueDesactivada() {
		Usuario usuario = TestDatos.usuario(1L, Rol.HUESPED, "hash");
		usuario.desactivar();
		usuario.restringir("Fraude");
		assertThat(usuario.getEstadoCuenta()).isEqualTo(EstadoCuenta.RESTRINGIDA);

		usuario.levantarRestriccion();

		// Solo su dueño la reactiva, confirmando con sus credenciales
		assertThat(usuario.getEstadoCuenta()).isEqualTo(EstadoCuenta.DESACTIVADA);
		usuario.reactivar();
		usuario.restringir("Otra vez");
		usuario.levantarRestriccion();
		assertThat(usuario.getEstadoCuenta()).isEqualTo(EstadoCuenta.ACTIVA);
	}

	@Test
	void unaCuentaRestringidaNoSeDesactivaNiSeReactiva() {
		Usuario usuario = TestDatos.usuario(1L, Rol.HUESPED, "hash");
		usuario.restringir("Fraude");

		assertThatThrownBy(usuario::desactivar).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(usuario::reactivar).isInstanceOf(ReglaNegocioException.class);
		assertThat(usuario.getEstadoCuenta()).isEqualTo(EstadoCuenta.RESTRINGIDA);
	}
}
