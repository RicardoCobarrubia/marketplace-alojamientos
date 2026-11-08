package co.edu.unbosque.marketplace.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.model.Usuario;
import co.edu.unbosque.marketplace.model.Usuario.EstadoCuenta;
import co.edu.unbosque.marketplace.model.Usuario.Rol;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

	@Mock
	private JwtUtil jwtUtil;
	@Mock
	private UserDetailsServiceImpl userDetailsService;

	private final Usuario usuario = TestDatos.usuario(5L, Rol.ANFITRION, "hash");

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	/** Ejecuta el filtro y devuelve la autenticación que quedó durante la petición. */
	private Authentication filtrar(String cabecera) throws Exception {
		MockHttpServletRequest peticion = new MockHttpServletRequest();
		if (cabecera != null) {
			peticion.addHeader("Authorization", cabecera);
		}
		Authentication[] durante = new Authentication[1];
		new JwtAuthenticationFilter(jwtUtil, userDetailsService)
				.doFilter(peticion, new MockHttpServletResponse(),
						(req, res) -> durante[0] = SecurityContextHolder.getContext().getAuthentication());
		SecurityContextHolder.clearContext();
		return durante[0];
	}

	@Test
	void conTokenValidoAutenticaConElRol() throws Exception {
		when(jwtUtil.extraerUsername("t")).thenReturn(Optional.of("ana@correo.com"));
		when(userDetailsService.cargarParaPeticion("ana@correo.com")).thenReturn(Optional.of(usuario));
		when(jwtUtil.validarToken("t", usuario)).thenReturn(true);

		Authentication autenticacion = filtrar("Bearer t");

		assertThat(autenticacion).isNotNull();
		assertThat(autenticacion.getPrincipal()).isSameAs(usuario);
		assertThat(autenticacion.getAuthorities()).extracting(GrantedAuthority::getAuthority)
				.containsExactly("ROLE_ANFITRION");
	}

	@Test
	void conVersionDeSesionVieja_noAutentica() throws Exception {
		when(jwtUtil.extraerUsername("t")).thenReturn(Optional.of("ana@correo.com"));
		when(userDetailsService.cargarParaPeticion("ana@correo.com")).thenReturn(Optional.of(usuario));
		when(jwtUtil.validarToken("t", usuario)).thenReturn(false);

		assertThat(filtrar("Bearer t")).isNull();
	}

	@Test
	void conCuentaNoActiva_noAutentica() throws Exception {
		TestDatos.asignar(usuario, "estadoCuenta", EstadoCuenta.RESTRINGIDA);
		when(jwtUtil.extraerUsername("t")).thenReturn(Optional.of("ana@correo.com"));
		when(userDetailsService.cargarParaPeticion("ana@correo.com")).thenReturn(Optional.of(usuario));

		assertThat(filtrar("Bearer t")).isNull();
	}

	@Test
	void sinCabeceraOConTokenInvalido_noAutentica() throws Exception {
		when(jwtUtil.extraerUsername("malo")).thenReturn(Optional.empty());

		assertThat(filtrar(null)).isNull();
		assertThat(filtrar("Bearer malo")).isNull();
		assertThat(filtrar("Basic abc")).isNull();
	}
}
