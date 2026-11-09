package co.edu.unbosque.marketplace.alojamientos.presentacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import co.edu.unbosque.marketplace.TestcontainersConfiguration;

/**
 * Flujo de alojamientos contra PostgreSQL real (Testcontainers): MA-53 a MA-57.
 * Requiere Docker en ejecución.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class AlojamientoIntegrationTest {

	private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern ID = Pattern.compile("^\\{\"id\"\\s*:\\s*(\\d+)");
	private static final String CLAVE = "clave-segura-1";

	@Autowired
	private WebApplicationContext contexto;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private CacheManager cacheManager;

	private MockMvc mvc;
	private String anfitrion;
	private String otroAnfitrion;
	private String huesped;
	private String administrador;

	@BeforeEach
	void preparar() throws Exception {
		mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
		jdbc.execute("TRUNCATE TABLE usuario RESTART IDENTITY CASCADE");
		cacheManager.getCacheNames().forEach(nombre -> cacheManager.getCache(nombre).clear());

		anfitrion = registrarEIniciarSesion("anfitrion@correo.com", "ANFITRION");
		otroAnfitrion = registrarEIniciarSesion("otro@correo.com", "ANFITRION");
		huesped = registrarEIniciarSesion("huesped@correo.com", "HUESPED");
		jdbc.update("INSERT INTO usuario (nombre, email, telefono, password_hash, rol) VALUES (?, ?, ?, ?, ?)",
				"Admin", "admin@correo.com", "0000000", passwordEncoder.encode(CLAVE), "ADMINISTRADOR");
		administrador = iniciarSesion("admin@correo.com");
	}

	// ─────────────────────────────────────────
	// Ayudas
	// ─────────────────────────────────────────

	private String registrarEIniciarSesion(String email, String rol) throws Exception {
		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
				{"nombre":"Usuario","email":"%s","telefono":"3001234567","password":"%s","rol":"%s"}"""
				.formatted(email, CLAVE, rol))).andExpect(status().isCreated());
		return iniciarSesion(email);
	}

	private String iniciarSesion(String email) throws Exception {
		String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, CLAVE)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		Matcher token = TOKEN.matcher(respuesta);
		assertThat(token.find()).isTrue();
		return token.group(1);
	}

	private static String alojamiento(String titulo, int capacidad, String precio, int imagenes) {
		StringBuilder rutas = new StringBuilder();
		for (int i = 1; i <= imagenes; i++) {
			rutas.append(i > 1 ? "," : "").append("\"https://img.ejemplo.com/").append(i).append(".jpg\"");
		}
		return """
				{"titulo":"%s","descripcion":"Dos habitaciones con vista al mar","ubicacion":"El Rodadero",
				 "ciudadId":4,"tipoId":1,"politicaCancelacionId":1,"capacidad":%d,"precioNoche":%s,
				 "tarifaLimpieza":40000,"servicioIds":[1,2],"reglas":["No fumar","No fiestas"],
				 "imagenes":[%s]}""".formatted(titulo, capacidad, precio, rutas);
	}

	private ResultActions como(String token, MockHttpServletRequestBuilder peticion) throws Exception {
		return mvc.perform(peticion.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON));
	}

	private long crear(String token) throws Exception {
		String respuesta = como(token, post("/api/alojamientos").content(alojamiento("Apto frente al mar", 4, "250000", 2)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		Matcher id = ID.matcher(respuesta);
		assertThat(id.find()).isTrue();
		return Long.parseLong(id.group(1));
	}

	// ─────────────────────────────────────────
	// MA-53 Registrar
	// ─────────────────────────────────────────

	@Test
	void elAnfitrionRegistraUnAlojamientoEnBorrador() throws Exception {
		como(anfitrion, post("/api/alojamientos").content(alojamiento("Apto frente al mar", 4, "250000", 2)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("BORRADOR"))
				.andExpect(jsonPath("$.ciudad.nombre").value("Santa Marta"))
				.andExpect(jsonPath("$.imagenes.length()").value(2))
				.andExpect(jsonPath("$.reglas[0]").value("No fumar"));

		como(anfitrion, get("/api/alojamientos/mios"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].imagenes[0]").value("https://img.ejemplo.com/1.jpg"));
	}

	@Test
	void ignoraLosCamposQueSoloPoneElSistema() throws Exception {
		String conCamposProhibidos = alojamiento("Apto", 4, "250000", 1)
				.replace("{\"titulo\"", "{\"estado\":\"ACTIVA\",\"anfitrionId\":999,\"id\":777,\"titulo\"");

		como(anfitrion, post("/api/alojamientos").content(conCamposProhibidos))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("BORRADOR"))
				.andExpect(jsonPath("$.anfitrionId").value(1))
				.andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(777)));
	}

	@Test
	void rechazaDatosInvalidosYAlHuesped() throws Exception {
		como(anfitrion, post("/api/alojamientos").content(alojamiento("X", 0, "-1", 11)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.capacidad").exists())
				.andExpect(jsonPath("$.errores.precioNoche").exists())
				.andExpect(jsonPath("$.errores.imagenes").exists());
		como(anfitrion, post("/api/alojamientos").content(alojamiento("Apto", 4, "250000", 0)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.imagenes").exists());

		como(huesped, post("/api/alojamientos").content(alojamiento("Apto", 4, "250000", 2)))
				.andExpect(status().isForbidden());
	}

	@Test
	void losCatalogosSonPublicosYElAdministradorLosAmplia() throws Exception {
		mvc.perform(get("/api/catalogos/ciudades")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].nombre").exists());

		como(administrador, post("/api/admin/catalogos/tipos").content("{\"codigo\":\"GLAMPING\",\"nombre\":\"Glamping\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.codigo").value("GLAMPING"));

		como(anfitrion, post("/api/admin/catalogos/tipos").content("{\"codigo\":\"OTRO\",\"nombre\":\"Otro\"}"))
				.andExpect(status().isForbidden());
	}

	// ─────────────────────────────────────────
	// MA-54 Editar
	// ─────────────────────────────────────────

	@Test
	void soloElDuenoOElAdministradorEditan() throws Exception {
		long id = crear(anfitrion);

		como(otroAnfitrion, put("/api/alojamientos/" + id).content(alojamiento("Mío", 2, "100000", 1)))
				.andExpect(status().isForbidden());

		como(anfitrion, put("/api/alojamientos/" + id).content(alojamiento("Título nuevo", 6, "300000", 1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.titulo").value("Título nuevo"))
				.andExpect(jsonPath("$.capacidad").value(6));

		como(administrador, put("/api/alojamientos/" + id).content(alojamiento("Corregido", 6, "300000", 1)))
				.andExpect(status().isOk());
	}

	// ─────────────────────────────────────────
	// MA-55 Servicios adicionales
	// ─────────────────────────────────────────

	@Test
	void elDuenoDefineServiciosAdicionales() throws Exception {
		long id = crear(anfitrion);

		String respuesta = como(anfitrion, post("/api/alojamientos/" + id + "/servicios-adicionales")
				.content("{\"nombre\":\"Desayuno\",\"costo\":30000,\"modalidad\":\"POR_NOCHE\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.modalidad").value("POR_NOCHE"))
				.andReturn().getResponse().getContentAsString();
		Matcher servicioId = ID.matcher(respuesta);
		assertThat(servicioId.find()).isTrue();

		como(anfitrion, post("/api/alojamientos/" + id + "/servicios-adicionales")
				.content("{\"nombre\":\"Traslado\",\"costo\":0,\"modalidad\":\"POR_SEMANA\"}"))
				.andExpect(status().isBadRequest());

		como(otroAnfitrion, post("/api/alojamientos/" + id + "/servicios-adicionales")
				.content("{\"nombre\":\"Desayuno\",\"costo\":30000,\"modalidad\":\"POR_NOCHE\"}"))
				.andExpect(status().isForbidden());

		como(anfitrion, delete("/api/alojamientos/" + id + "/servicios-adicionales/" + servicioId.group(1)))
				.andExpect(status().isNoContent());
		como(anfitrion, get("/api/alojamientos/" + id))
				.andExpect(jsonPath("$.serviciosAdicionales.length()").value(0));
	}

	@Test
	void conElAlojamientoBloqueadoNoSeCambianLosServiciosAdicionales() throws Exception {
		long id = crear(anfitrion);
		jdbc.update("UPDATE alojamiento SET estado = 'BLOQUEADA' WHERE id = ?", id);

		como(anfitrion, post("/api/alojamientos/" + id + "/servicios-adicionales")
				.content("{\"nombre\":\"Desayuno\",\"costo\":30000,\"modalidad\":\"POR_NOCHE\"}"))
				.andExpect(status().isForbidden());
	}

	// ─────────────────────────────────────────
	// MA-56 y MA-57 Estados y revisión
	// ─────────────────────────────────────────

	@Test
	void cicloCompletoDePublicacion() throws Exception {
		long id = crear(anfitrion);

		como(huesped, get("/api/alojamientos/" + id)).andExpect(status().isNotFound());

		como(anfitrion, post("/api/alojamientos/" + id + "/enviar-revision"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PENDIENTE_REVISION"));

		como(administrador, get("/api/admin/alojamientos?estado=PENDIENTE_REVISION"))
				.andExpect(jsonPath("$.length()").value(1));

		como(administrador, post("/api/admin/alojamientos/" + id + "/rechazar")
				.content("{\"motivoRechazo\":\"Faltan fotos del baño\"}"))
				.andExpect(jsonPath("$.estado").value("BORRADOR"))
				.andExpect(jsonPath("$.motivoRechazo").value("Faltan fotos del baño"));

		como(anfitrion, post("/api/alojamientos/" + id + "/enviar-revision")).andExpect(status().isOk());
		como(administrador, post("/api/admin/alojamientos/" + id + "/aprobar"))
				.andExpect(jsonPath("$.estado").value("ACTIVA"));

		como(huesped, get("/api/alojamientos/" + id)).andExpect(status().isOk());

		como(anfitrion, post("/api/alojamientos/" + id + "/pausar"))
				.andExpect(jsonPath("$.estado").value("PAUSADA"));
		como(anfitrion, post("/api/alojamientos/" + id + "/reanudar"))
				.andExpect(jsonPath("$.estado").value("ACTIVA"));
	}

	@Test
	void rechazaTransicionesInvalidasYRevisionPorOtroRol() throws Exception {
		long id = crear(anfitrion);

		como(anfitrion, post("/api/alojamientos/" + id + "/pausar"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));

		como(anfitrion, post("/api/alojamientos/" + id + "/enviar-revision")).andExpect(status().isOk());
		// El anfitrión no puede saltarse la aprobación del administrador
		como(anfitrion, post("/api/alojamientos/" + id + "/reanudar"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
		como(anfitrion, post("/api/admin/alojamientos/" + id + "/aprobar")).andExpect(status().isForbidden());
		como(otroAnfitrion, post("/api/alojamientos/" + id + "/pausar")).andExpect(status().isForbidden());
	}

	@Test
	void elAnfitrionNoSacaUnAlojamientoBloqueado() throws Exception {
		long id = crear(anfitrion);
		jdbc.update("UPDATE alojamiento SET estado = 'BLOQUEADA' WHERE id = ?", id);

		como(anfitrion, post("/api/alojamientos/" + id + "/pausar"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
		como(anfitrion, post("/api/alojamientos/" + id + "/reanudar"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
	}

	@Test
	void rechazarExigeElMotivo() throws Exception {
		long id = crear(anfitrion);
		como(anfitrion, post("/api/alojamientos/" + id + "/enviar-revision")).andExpect(status().isOk());

		como(administrador, post("/api/admin/alojamientos/" + id + "/rechazar")
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.motivoRechazo").exists());
	}
}
