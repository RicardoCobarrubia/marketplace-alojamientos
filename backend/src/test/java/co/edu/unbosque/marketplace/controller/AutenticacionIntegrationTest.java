package co.edu.unbosque.marketplace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import co.edu.unbosque.marketplace.TestcontainersConfiguration;

/**
 * Flujo completo contra PostgreSQL real (Testcontainers): registro, inicio de sesión y acceso
 * con y sin token. Cubre MA-47, MA-48 y MA-49 escenario 1. Requiere Docker en ejecución.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class AutenticacionIntegrationTest {

	private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

	@Autowired
	private WebApplicationContext contexto;
	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc mvc;

	@BeforeEach
	void preparar() {
		mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
		jdbc.update("DELETE FROM usuario");
	}

	private static String registro(String email, String rol) {
		return """
				{"nombre":"Ana Pérez","email":"%s","telefono":"3001234567",
				 "password":"clave-segura-1","rol":"%s"}""".formatted(email, rol);
	}

	private static String login(String email, String password) {
		return """
				{"email":"%s","password":"%s"}""".formatted(email, password);
	}

	@Test
	void registroLoginYConsultaDeMisDatos() throws Exception {
		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
						.content(registro("Ana@Correo.com", "HUESPED")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("ana@correo.com"))
				.andExpect(jsonPath("$.rol").value("HUESPED"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist());

		String respuestaLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(login("ana@correo.com", "clave-segura-1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tipo").value("Bearer"))
				.andExpect(jsonPath("$.usuario.rol").value("HUESPED"))
				.andReturn().getResponse().getContentAsString();

		Matcher token = TOKEN.matcher(respuestaLogin);
		assertThat(token.find()).isTrue();

		mvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + token.group(1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("ana@correo.com"));
	}

	@Test
	void laContrasenaSeGuardaComoHashBcrypt() throws Exception {
		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
						.content(registro("hash@correo.com", "ANFITRION")))
				.andExpect(status().isCreated());

		String hash = jdbc.queryForObject("SELECT password_hash FROM usuario WHERE email = ?",
				String.class, "hash@correo.com");
		assertThat(hash).startsWith("$2").doesNotContain("clave-segura-1");
	}

	@Test
	void rechazaCorreoDuplicadoYRolAdministrador() throws Exception {
		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
						.content(registro("dup@correo.com", "HUESPED")))
				.andExpect(status().isCreated());

		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
						.content(registro("DUP@correo.com", "ANFITRION")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CORREO_YA_REGISTRADO"));

		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
						.content(registro("admin@correo.com", "ADMINISTRADOR")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("ROL_NO_PERMITIDO"));
	}

	@Test
	void conCredencialesInvalidasDaUnErrorGenerico() throws Exception {
		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
						.content(registro("ana@correo.com", "HUESPED")))
				.andExpect(status().isCreated());

		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(login("ana@correo.com", "clave-incorrecta")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));

		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(login("nadie@correo.com", "clave-incorrecta")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
	}

	@Test
	void elEstadoDeLaCuentaSoloSeRevelaConLaContrasenaCorrecta() throws Exception {
		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
						.content(registro("restringida@correo.com", "HUESPED")))
				.andExpect(status().isCreated());
		jdbc.update("UPDATE usuario SET estado_cuenta = 'RESTRINGIDA' WHERE email = ?", "restringida@correo.com");

		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(login("restringida@correo.com", "clave-incorrecta")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));

		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(login("restringida@correo.com", "clave-segura-1")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("CUENTA_RESTRINGIDA"));
	}

	@Test
	void sinTokenOConTokenFalsoResponde401() throws Exception {
		mvc.perform(get("/api/usuarios/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

		mvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer token.falso.123"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unHuespedNoEntraARutasDeAdministracion() throws Exception {
		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
						.content(registro("huesped@correo.com", "HUESPED")))
				.andExpect(status().isCreated());
		String respuestaLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content(login("huesped@correo.com", "clave-segura-1")))
				.andReturn().getResponse().getContentAsString();
		Matcher token = TOKEN.matcher(respuestaLogin);
		assertThat(token.find()).isTrue();

		mvc.perform(get("/api/admin/cualquier-ruta").header("Authorization", "Bearer " + token.group(1)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
	}

	@Test
	void datosInvalidosIndicanElCampo() throws Exception {
		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"","email":"no-es-correo","telefono":"12",
								 "password":"corta","rol":"HUESPED"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
				.andExpect(jsonPath("$.errores.email").exists())
				.andExpect(jsonPath("$.errores.password").exists());
	}
}
