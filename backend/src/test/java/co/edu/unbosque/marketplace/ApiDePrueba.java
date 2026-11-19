package co.edu.unbosque.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import co.edu.unbosque.marketplace.comun.web.CorrelacionFilter;

/**
 * Ayudas para las pruebas de integración contra la API: usuarios, sesión y alojamientos
 * activos. Cada prueba parte de una base de datos sin usuarios.
 */
public class ApiDePrueba {

	public static final String CLAVE = "clave-segura-1";

	private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern ID = Pattern.compile("^\\{\"id\"\\s*:\\s*(\\d+)");

	private final MockMvc mvc;
	private final JdbcTemplate jdbc;
	private final PasswordEncoder passwordEncoder;

	public ApiDePrueba(WebApplicationContext contexto, JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
			CacheManager cacheManager) {
		// El filtro de correlación va primero, como en la aplicación real (ADR-012)
		this.mvc = MockMvcBuilders.webAppContextSetup(contexto)
				.addFilters(contexto.getBean(CorrelacionFilter.class))
				.apply(springSecurity())
				.build();
		this.jdbc = jdbc;
		this.passwordEncoder = passwordEncoder;
		jdbc.execute("TRUNCATE TABLE usuario RESTART IDENTITY CASCADE");
		cacheManager.getCacheNames().forEach(nombre -> cacheManager.getCache(nombre).clear());
	}

	public MockMvc mvc() {
		return mvc;
	}

	public String registrarEIniciarSesion(String email, String rol) throws Exception {
		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
				{"nombre":"Usuario","email":"%s","telefono":"3001234567","password":"%s","rol":"%s"}"""
				.formatted(email, CLAVE, rol))).andExpect(status().isCreated());
		return iniciarSesion(email);
	}

	/** Nadie puede registrarse como administrador: se inserta directamente. */
	public String administrador() throws Exception {
		jdbc.update("INSERT INTO usuario (nombre, email, telefono, password_hash, rol) VALUES (?, ?, ?, ?, ?)",
				"Admin", "admin@correo.com", "0000000", passwordEncoder.encode(CLAVE), "ADMINISTRADOR");
		return iniciarSesion("admin@correo.com");
	}

	public String iniciarSesion(String email) throws Exception {
		String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, CLAVE)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		Matcher token = TOKEN.matcher(respuesta);
		assertThat(token.find()).isTrue();
		return token.group(1);
	}

	public ResultActions como(String token, MockHttpServletRequestBuilder peticion) throws Exception {
		return mvc.perform(peticion.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON));
	}

	public static long id(String json) {
		Matcher id = ID.matcher(json);
		assertThat(id.find()).isTrue();
		return Long.parseLong(id.group(1));
	}

	/**
	 * Alojamiento aprobado por el administrador: precio por noche 200.000, limpieza 30.000 y
	 * capacidad 4.
	 */
	public long crearAlojamientoActivo(String anfitrion, String administrador) throws Exception {
		String respuesta = como(anfitrion, post("/api/alojamientos").content("""
				{"titulo":"Cabaña","descripcion":"Cabaña en el bosque","ubicacion":"Vereda El Retiro",
				 "ciudadId":2,"tipoId":3,"politicaCancelacionId":1,"capacidad":4,"precioNoche":200000,
				 "tarifaLimpieza":30000,"servicioIds":[1],"reglas":["No fumar"],"imagenes":["https://img.ejemplo.com/1.jpg"]}"""))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long id = id(respuesta);
		como(anfitrion, post("/api/alojamientos/" + id + "/enviar-revision")).andExpect(status().isOk());
		como(administrador, post("/api/admin/alojamientos/" + id + "/aprobar")).andExpect(status().isOk());
		return id;
	}

	public long crearServicioAdicional(String anfitrion, long alojamientoId, String nombre, int costo, String modalidad)
			throws Exception {
		String respuesta = como(anfitrion, post("/api/alojamientos/" + alojamientoId + "/servicios-adicionales")
				.content("{\"nombre\":\"%s\",\"costo\":%d,\"modalidad\":\"%s\"}".formatted(nombre, costo, modalidad)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return id(respuesta);
	}
}
