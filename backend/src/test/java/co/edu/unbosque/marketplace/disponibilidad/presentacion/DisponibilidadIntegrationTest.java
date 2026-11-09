package co.edu.unbosque.marketplace.disponibilidad.presentacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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
 * Disponibilidad contra PostgreSQL real (Testcontainers): MA-58, MA-59 y la restricción de
 * exclusión de ADR-003. Requiere Docker en ejecución.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class DisponibilidadIntegrationTest {

	private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern ID = Pattern.compile("^\\{\"id\"\\s*:\\s*(\\d+)");
	private static final String CLAVE = "clave-segura-1";
	private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));

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
	private long alojamientoId;

	@BeforeEach
	void preparar() throws Exception {
		mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
		jdbc.execute("TRUNCATE TABLE usuario RESTART IDENTITY CASCADE");
		cacheManager.getCacheNames().forEach(nombre -> cacheManager.getCache(nombre).clear());

		anfitrion = registrarEIniciarSesion("anfitrion@correo.com", "ANFITRION");
		otroAnfitrion = registrarEIniciarSesion("otro@correo.com", "ANFITRION");
		jdbc.update("INSERT INTO usuario (nombre, email, telefono, password_hash, rol) VALUES (?, ?, ?, ?, ?)",
				"Admin", "admin@correo.com", "0000000", passwordEncoder.encode(CLAVE), "ADMINISTRADOR");
		String administrador = iniciarSesion("admin@correo.com");

		alojamientoId = crearAlojamientoActivo(administrador);
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

	private ResultActions como(String token, MockHttpServletRequestBuilder peticion) throws Exception {
		return mvc.perform(peticion.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON));
	}

	private static long id(String json) {
		Matcher id = ID.matcher(json);
		assertThat(id.find()).isTrue();
		return Long.parseLong(id.group(1));
	}

	private long crearAlojamientoActivo(String administrador) throws Exception {
		String respuesta = como(anfitrion, post("/api/alojamientos").content("""
				{"titulo":"Cabaña","descripcion":"Cabaña en el bosque","ubicacion":"Vereda El Retiro",
				 "ciudadId":2,"tipoId":3,"politicaCancelacionId":1,"capacidad":4,"precioNoche":200000,
				 "tarifaLimpieza":0,"servicioIds":[1],"reglas":["No fumar"],"imagenes":["https://img.ejemplo.com/1.jpg"]}"""))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long id = id(respuesta);
		como(anfitrion, post("/api/alojamientos/" + id + "/enviar-revision")).andExpect(status().isOk());
		como(administrador, post("/api/admin/alojamientos/" + id + "/aprobar")).andExpect(status().isOk());
		return id;
	}

	private String rutaPeriodos() {
		return "/api/alojamientos/" + alojamientoId + "/periodos";
	}

	private long crearPeriodo(LocalDate inicio, LocalDate fin) throws Exception {
		String respuesta = como(anfitrion, post(rutaPeriodos())
				.content("{\"fechaInicio\":\"%s\",\"fechaFin\":\"%s\"}".formatted(inicio, fin)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return id(respuesta);
	}

	private ResultActions consultar(LocalDate llegada, LocalDate salida) throws Exception {
		return mvc.perform(get("/api/alojamientos/" + alojamientoId + "/disponibilidad")
				.param("llegada", llegada.toString()).param("salida", salida.toString()));
	}

	private void reservar(LocalDate llegada, LocalDate salida, String retenidoHasta) {
		jdbc.update("INSERT INTO periodo_reservado (alojamiento_id, fecha_llegada, fecha_salida, retenido_hasta) "
				+ "VALUES (?, ?, ?, " + retenidoHasta + ")", alojamientoId, llegada, salida);
	}

	// ─────────────────────────────────────────
	// MA-58 Periodos
	// ─────────────────────────────────────────

	@Test
	void elDuenoGestionaSusPeriodos() throws Exception {
		long periodo = crearPeriodo(HOY.plusDays(1), HOY.plusDays(31));

		como(anfitrion, put(rutaPeriodos() + "/" + periodo)
				.content("{\"fechaInicio\":\"%s\",\"fechaFin\":\"%s\"}".formatted(HOY.plusDays(1), HOY.plusDays(60))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fechaFin").value(HOY.plusDays(60).toString()));

		mvc.perform(get(rutaPeriodos())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

		como(anfitrion, delete(rutaPeriodos() + "/" + periodo)).andExpect(status().isNoContent());
		mvc.perform(get(rutaPeriodos())).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void rechazaFechasInvalidasCrucesYOtroUsuario() throws Exception {
		crearPeriodo(HOY.plusDays(1), HOY.plusDays(31));

		como(anfitrion, post(rutaPeriodos())
				.content("{\"fechaInicio\":\"%s\",\"fechaFin\":\"%s\"}".formatted(HOY.plusDays(10), HOY.plusDays(5))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("FECHAS_INVALIDAS"));

		como(anfitrion, post(rutaPeriodos())
				.content("{\"fechaInicio\":\"%s\",\"fechaFin\":\"%s\"}".formatted(HOY.plusDays(20), HOY.plusDays(40))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PERIODO_SUPERPUESTO"));

		como(otroAnfitrion, post(rutaPeriodos())
				.content("{\"fechaInicio\":\"%s\",\"fechaFin\":\"%s\"}".formatted(HOY.plusDays(40), HOY.plusDays(50))))
				.andExpect(status().isForbidden());
	}

	@Test
	void noSeEliminaNiReducePeriodoConReservas() throws Exception {
		long periodo = crearPeriodo(HOY.plusDays(1), HOY.plusDays(31));
		reservar(HOY.plusDays(20), HOY.plusDays(25), "NULL");

		como(anfitrion, delete(rutaPeriodos() + "/" + periodo))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PERIODO_CON_RESERVAS"));

		como(anfitrion, put(rutaPeriodos() + "/" + periodo)
				.content("{\"fechaInicio\":\"%s\",\"fechaFin\":\"%s\"}".formatted(HOY.plusDays(1), HOY.plusDays(10))))
				.andExpect(status().isConflict());
	}

	// ─────────────────────────────────────────
	// MA-59 Consulta
	// ─────────────────────────────────────────

	@Test
	void consultaDisponibilidadSinSesion() throws Exception {
		crearPeriodo(HOY.plusDays(1), HOY.plusDays(31));
		reservar(HOY.plusDays(10), HOY.plusDays(12), "NULL");
		reservar(HOY.plusDays(15), HOY.plusDays(17), "now() - interval '1 minute'");

		consultar(HOY.plusDays(2), HOY.plusDays(5))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.disponible").value(true))
				.andExpect(jsonPath("$.noches").value(3));

		consultar(HOY.plusDays(11), HOY.plusDays(13))
				.andExpect(jsonPath("$.disponible").value(false))
				.andExpect(jsonPath("$.motivo").value("OCUPADO"));

		// Sale el mismo día que llega la reserva: no se cruzan
		consultar(HOY.plusDays(8), HOY.plusDays(10)).andExpect(jsonPath("$.disponible").value(true));

		// Retención vencida: ya no ocupa las fechas
		consultar(HOY.plusDays(15), HOY.plusDays(17)).andExpect(jsonPath("$.disponible").value(true));

		consultar(HOY.plusDays(30), HOY.plusDays(33))
				.andExpect(jsonPath("$.disponible").value(false))
				.andExpect(jsonPath("$.motivo").value("FUERA_DE_PERIODO"));
	}

	@Test
	void validaLasFechasDeLaConsulta() throws Exception {
		consultar(HOY.minusDays(1), HOY.plusDays(2))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("FECHAS_INVALIDAS"));
		consultar(HOY.plusDays(3), HOY.plusDays(3)).andExpect(status().isBadRequest());
	}

	// ─────────────────────────────────────────
	// ADR-003 Restricción de exclusión
	// ─────────────────────────────────────────

	@Test
	void laBaseDeDatosImpideReservasActivasSuperpuestas() {
		reservar(HOY.plusDays(10), HOY.plusDays(15), "NULL");

		LocalDate llegada = HOY.plusDays(12);
		LocalDate salida = HOY.plusDays(14);

		assertThatThrownBy(() -> reservar(llegada, salida, "NULL"))
				.isInstanceOf(DataIntegrityViolationException.class);

		// Una reserva liberada ya no bloquea
		jdbc.update("UPDATE periodo_reservado SET liberado_en = now()");
		reservar(llegada, salida, "NULL");
	}
}
