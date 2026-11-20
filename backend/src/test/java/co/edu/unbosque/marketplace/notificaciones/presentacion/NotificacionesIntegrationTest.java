package co.edu.unbosque.marketplace.notificaciones.presentacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;

import co.edu.unbosque.marketplace.ApiDePrueba;
import co.edu.unbosque.marketplace.TestcontainersConfiguration;
import co.edu.unbosque.marketplace.notificaciones.aplicacion.ProcesadorOutbox;
import co.edu.unbosque.marketplace.reservas.presentacion.PasarelaDePrueba;
import co.edu.unbosque.marketplace.reservas.presentacion.PasarelaDePrueba.Modo;
import co.edu.unbosque.marketplace.reservas.presentacion.PasarelaDePruebaConfig;

/**
 * Notificaciones generadas por el procesador del outbox contra PostgreSQL real (MA-75, MA-76).
 * Requiere Docker en ejecución.
 */
@Import({ TestcontainersConfiguration.class, PasarelaDePruebaConfig.class })
@SpringBootTest
@ActiveProfiles("test")
class NotificacionesIntegrationTest {

	private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));

	@Autowired
	private WebApplicationContext contexto;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private CacheManager cacheManager;
	@Autowired
	private PasarelaDePrueba pasarela;
	@Autowired
	private ProcesadorOutbox procesador;

	private ApiDePrueba api;
	private String anfitrion;
	private String huesped;
	private String administrador;
	private long alojamientoId;

	@BeforeEach
	void preparar() throws Exception {
		jdbc.execute("TRUNCATE TABLE evento_outbox RESTART IDENTITY");
		api = new ApiDePrueba(contexto, jdbc, passwordEncoder, cacheManager);
		pasarela.reiniciar();
		anfitrion = api.registrarEIniciarSesion("anfitrion@correo.com", "ANFITRION");
		huesped = api.registrarEIniciarSesion("huesped@correo.com", "HUESPED");
		administrador = api.administrador();
		alojamientoId = api.crearAlojamientoActivo(anfitrion, administrador);
		api.como(anfitrion, post("/api/alojamientos/" + alojamientoId + "/periodos")
				.content("{\"fechaInicio\":\"%s\",\"fechaFin\":\"%s\"}".formatted(HOY, HOY.plusDays(60))))
				.andExpect(status().isCreated());
	}

	private long reservar(String clave, int desde) throws Exception {
		return ApiDePrueba.id(api.como(huesped, post("/api/reservas").header("Idempotency-Key", clave).content("""
				{"alojamientoId":%d,"llegada":"%s","salida":"%s","huespedes":2}"""
				.formatted(alojamientoId, HOY.plusDays(desde), HOY.plusDays(desde + 3))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private int pendientes() {
		Integer cantidad = jdbc.queryForObject("SELECT count(*) FROM evento_outbox WHERE estado = 'PENDIENTE'",
				Integer.class);
		return cantidad == null ? 0 : cantidad;
	}

	// ─────────────────────────────────────────
	// MA-75: generar notificaciones
	// ─────────────────────────────────────────

	@Test
	void unaReservaConfirmadaSeNotificaAlHuespedYAlAnfitrion() throws Exception {
		long reserva = reservar("r-1", 10);
		// La reserva se completa sin esperar a las notificaciones: el evento espera en el outbox
		assertThat(pendientes()).isPositive();

		assertThat(procesador.procesarPendientes()).isPositive();

		assertThat(pendientes()).isZero();
		api.como(huesped, get("/api/notificaciones"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].tipo").value("RESERVA_CONFIRMADA"))
				.andExpect(jsonPath("$[0].mensaje").value("Tu reserva #%d del %s al %s quedó confirmada."
						.formatted(reserva, HOY.plusDays(10), HOY.plusDays(13))))
				.andExpect(jsonPath("$[0].leidaEn").doesNotExist());
		// El anfitrión también recibió la aprobación de su alojamiento; la reserva es la más reciente
		api.como(anfitrion, get("/api/notificaciones"))
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].tipo").value("RESERVA_CONFIRMADA"))
				.andExpect(jsonPath("$[1].tipo").value("ALOJAMIENTO_APROBADO"));
	}

	@Test
	void unPagoFallidoSoloSeNotificaAlHuesped() throws Exception {
		pasarela.modo(Modo.RECHAZAR);
		api.como(huesped, post("/api/reservas").header("Idempotency-Key", "r-1").content("""
				{"alojamientoId":%d,"llegada":"%s","salida":"%s","huespedes":2}"""
				.formatted(alojamientoId, HOY.plusDays(10), HOY.plusDays(13))))
				.andExpect(status().isPaymentRequired());

		procesador.procesarPendientes();

		api.como(huesped, get("/api/notificaciones")).andExpect(jsonPath("$[0].tipo").value("PAGO_FALLIDO"));
		api.como(anfitrion, get("/api/notificaciones"))
				.andExpect(jsonPath("$[?(@.tipo == 'PAGO_FALLIDO')]", hasSize(0)));
	}

	@Test
	void alBloquearElAlojamientoSeAvisaAlAnfitrionYLosAdministradoresRevisan() throws Exception {
		reservar("r-1", 10);
		procesador.procesarPendientes();
		api.como(administrador, post("/api/admin/alojamientos/" + alojamientoId + "/bloqueo")
				.content("{\"motivo\":\"Fotos engañosas\"}")).andExpect(status().isOk());

		procesador.procesarPendientes();

		api.como(anfitrion, get("/api/notificaciones"))
				.andExpect(jsonPath("$[?(@.tipo == 'ALOJAMIENTO_BLOQUEADO')]", hasSize(1)))
				.andExpect(jsonPath("$[?(@.tipo == 'RESERVA_EN_REVISION')]", hasSize(1)));
		api.como(huesped, get("/api/notificaciones"))
				.andExpect(jsonPath("$[0].tipo").value("RESERVA_EN_REVISION"));
		api.como(administrador, get("/api/notificaciones"))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].titulo").value("Reserva por revisar"));
	}

	@Test
	void unaCancelacionSeNotificaConElValorDevuelto() throws Exception {
		long reserva = reservar("r-1", 10);
		api.como(huesped, post("/api/reservas/" + reserva + "/cancelacion").content("{\"motivo\":\"Cambio\"}"))
				.andExpect(status().isOk());

		procesador.procesarPendientes();

		api.como(huesped, get("/api/notificaciones"))
				.andExpect(jsonPath("$[?(@.tipo == 'RESERVA_CANCELADA')].mensaje",
						contains("La reserva #%d fue cancelada. Se te devolverán $693.000."
								.formatted(reserva))))
				.andExpect(jsonPath("$[?(@.tipo == 'DEVOLUCION_SOLICITADA')]", hasSize(1)));
	}

	@Test
	void procesarDosVecesNoDuplicaYUnEventoDanadoNoFrenaALosDemas() throws Exception {
		jdbc.update("INSERT INTO evento_outbox (tipo, agregado, agregado_id, payload, creado_en)"
				+ " VALUES ('PAGO_FALLIDO', 'RESERVA', 999, '{}'::jsonb, now())");
		reservar("r-1", 10);

		procesador.procesarPendientes();
		procesador.procesarPendientes();

		api.como(huesped, get("/api/notificaciones")).andExpect(jsonPath("$", hasSize(1)));
		assertThat(jdbc.queryForObject("SELECT intentos FROM evento_outbox WHERE agregado_id = 999", Integer.class))
				.isEqualTo(2);
		for (int i = 0; i < 3; i++) {
			procesador.procesarPendientes();
		}
		// Tras cinco intentos deja de reintentarse
		assertThat(jdbc.queryForObject("SELECT estado FROM evento_outbox WHERE agregado_id = 999", String.class))
				.isEqualTo("FALLIDO");
		assertThat(pendientes()).isZero();
	}

	// ─────────────────────────────────────────
	// MA-76: consultar mis notificaciones
	// ─────────────────────────────────────────

	@Test
	void cadaUnoVeYMarcaSoloLasSuyas() throws Exception {
		reservar("r-1", 10);
		procesador.procesarPendientes();
		String otro = api.registrarEIniciarSesion("otro@correo.com", "HUESPED");
		long id = jdbc.queryForObject("SELECT n.id FROM notificacion n JOIN usuario u ON u.id = n.usuario_id"
				+ " WHERE u.email = 'huesped@correo.com'", Long.class);

		api.como(otro, get("/api/notificaciones")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
		api.como(otro, post("/api/notificaciones/" + id + "/lectura")).andExpect(status().isNotFound());
		api.como(huesped, post("/api/notificaciones/" + id + "/lectura"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.leidaEn").isNotEmpty());
		api.mvc().perform(get("/api/notificaciones")).andExpect(status().isUnauthorized());
	}
}
