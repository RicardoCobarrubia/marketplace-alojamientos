package co.edu.unbosque.marketplace.moderacion.presentacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import co.edu.unbosque.marketplace.ApiDePrueba;
import co.edu.unbosque.marketplace.TestcontainersConfiguration;
import co.edu.unbosque.marketplace.reservas.aplicacion.ReservaDTO;
import co.edu.unbosque.marketplace.reservas.aplicacion.ReservaTransacciones;
import co.edu.unbosque.marketplace.reservas.presentacion.PasarelaDePrueba;
import co.edu.unbosque.marketplace.reservas.presentacion.PasarelaDePruebaConfig;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.infraestructura.UsuarioRepository;

/**
 * Moderación contra PostgreSQL real (MA-80 a MA-82) y su registro de auditoría (MA-87).
 * Requiere Docker en ejecución.
 */
@Import({ TestcontainersConfiguration.class, PasarelaDePruebaConfig.class })
@SpringBootTest
@ActiveProfiles("test")
class ModeracionIntegrationTest {

	private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));
	private static final LocalDate LLEGADA = HOY.plusDays(10);
	private static final LocalDate SALIDA = HOY.plusDays(13);
	private static final String MOTIVO = "{\"motivo\":\"Información engañosa\"}";

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
	private ReservaTransacciones transacciones;
	@Autowired
	private UsuarioRepository usuarioRepository;

	private ApiDePrueba api;
	private String anfitrion;
	private String huesped;
	private String administrador;
	private long alojamientoId;
	private long auditoriaDesde;

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
		// La auditoría no se puede vaciar: cada prueba solo mira los registros que ella crea,
		// sin los del registro, el inicio de sesión y el alojamiento de arriba
		Long ultimo = jdbc.queryForObject("SELECT max(id) FROM auditoria", Long.class);
		auditoriaDesde = ultimo == null ? 0 : ultimo;
	}

	private ResultActions reservar(String token, String clave, LocalDate llegada, LocalDate salida) throws Exception {
		return api.como(token, post("/api/reservas").header("Idempotency-Key", clave).content("""
				{"alojamientoId":%d,"llegada":"%s","salida":"%s","huespedes":2}"""
				.formatted(alojamientoId, llegada, salida)));
	}

	private long reservaConfirmada(String clave, LocalDate llegada, LocalDate salida) throws Exception {
		return ApiDePrueba.id(reservar(huesped, clave, llegada, salida).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	private long usuarioId(String email) {
		return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
	}

	private String estado(String tabla, long id) {
		String columna = "usuario".equals(tabla) ? "estado_cuenta" : "estado";
		return jdbc.queryForObject("SELECT " + columna + " FROM " + tabla + " WHERE id = ?", String.class, id);
	}

	/** Cuenta los registros de auditoría de esta prueba que cumplen la condición. */
	private int auditados(String condicion, Object... parametros) {
		return contar("SELECT count(*) FROM auditoria WHERE id > " + auditoriaDesde + " AND " + condicion, parametros);
	}

	private int contar(String sql, Object... parametros) {
		Integer cantidad = jdbc.queryForObject(sql, Integer.class, parametros);
		return cantidad == null ? 0 : cantidad;
	}

	private ResultActions login(String email) throws Exception {
		return api.mvc().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, ApiDePrueba.CLAVE)));
	}

	// ─────────────────────────────────────────
	// MA-80: bloquear y desbloquear alojamientos
	// ─────────────────────────────────────────

	@Test
	void alBloquearloSaleDeLaVistaNoAceptaReservasYSusReservasPasanARevision() throws Exception {
		long reserva = reservaConfirmada("r-1", LLEGADA, SALIDA);

		api.como(administrador, post("/api/admin/alojamientos/" + alojamientoId + "/bloqueo").content(MOTIVO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("BLOQUEADA"))
				.andExpect(jsonPath("$.motivo").value("Información engañosa"))
				.andExpect(jsonPath("$.reservasEnRevision").value(1));

		assertThat(estado("reserva", reserva)).isEqualTo("EN_REVISION");
		api.como(huesped, get("/api/alojamientos/" + alojamientoId)).andExpect(status().isNotFound());
		reservar(huesped, "r-2", HOY.plusDays(20), HOY.plusDays(22))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ALOJAMIENTO_NO_DISPONIBLE"));
		long admin = usuarioId("admin@correo.com");
		assertThat(auditados("accion = 'BLOQUEAR_ALOJAMIENTO' AND actor_id = ? AND actor_rol = 'ADMINISTRADOR'"
				+ " AND entidad_id = ? AND estado_anterior = 'ACTIVA' AND estado_nuevo = 'BLOQUEADA'"
				+ " AND resultado = 'EXITO' AND correlation_id IS NOT NULL", admin, alojamientoId)).isEqualTo(1);
		assertThat(auditados("accion = 'RESERVA_EN_REVISION' AND entidad_id = ?", reserva)).isEqualTo(1);

		// Al desbloquearlo queda PAUSADA y la reserva sigue en revisión, sin cancelarse (MA-82, escenario 3)
		api.como(administrador, post("/api/admin/alojamientos/" + alojamientoId + "/desbloqueo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAUSADA"));
		assertThat(estado("reserva", reserva)).isEqualTo("EN_REVISION");
		assertThat(contar("SELECT count(*) FROM reserva WHERE estado = 'CANCELADA'")).isZero();
	}

	@Test
	void soloCambianLasReservasConfirmadasFuturas() throws Exception {
		long terminada = reservaConfirmada("r-1", LLEGADA, SALIDA);
		jdbc.update("UPDATE reserva SET fecha_llegada = ?, fecha_salida = ? WHERE id = ?", HOY.minusDays(5),
				HOY.minusDays(2), terminada);
		long cancelada = reservaConfirmada("r-2", HOY.plusDays(20), HOY.plusDays(22));
		api.como(huesped, post("/api/reservas/" + cancelada + "/cancelacion").content(MOTIVO))
				.andExpect(status().isOk());
		long futura = reservaConfirmada("r-3", HOY.plusDays(30), HOY.plusDays(32));

		api.como(administrador, post("/api/admin/alojamientos/" + alojamientoId + "/bloqueo").content(MOTIVO))
				.andExpect(jsonPath("$.reservasEnRevision").value(1));
		api.como(administrador, post("/api/admin/usuarios/" + usuarioId("huesped@correo.com") + "/restriccion")
				.content(MOTIVO)).andExpect(jsonPath("$.reservasEnRevision").value(0));

		assertThat(estado("reserva", terminada)).isEqualTo("CONFIRMADA");
		assertThat(estado("reserva", cancelada)).isEqualTo("CANCELADA");
		assertThat(estado("reserva", futura)).isEqualTo("EN_REVISION");
	}

	@Test
	void unaReservaQueSePagaMientrasBloqueanElAlojamientoQuedaEnRevision() throws Exception {
		Usuario cliente = usuarioRepository.findByEmail("huesped@correo.com").orElseThrow();
		ReservaDTO solicitud = new ReservaDTO(null, alojamientoId, LLEGADA, SALIDA, 2, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null, null, null);
		// La reserva ya retuvo las fechas y se está cobrando cuando el administrador la bloquea
		long pagando = transacciones.iniciar(cliente, "pagando", "huella", solicitud).getId();
		api.como(administrador, post("/api/admin/alojamientos/" + alojamientoId + "/bloqueo").content(MOTIVO))
				.andExpect(jsonPath("$.reservasEnRevision").value(0));

		transacciones.confirmar(pagando, "pi_tarde");

		assertThat(estado("reserva", pagando)).isEqualTo("EN_REVISION");
		assertThat(jdbc.queryForList("SELECT tipo FROM evento_outbox WHERE agregado = 'RESERVA' AND agregado_id = ? ORDER BY id", String.class,
				pagando)).containsExactly("RESERVA_CONFIRMADA", "RESERVA_EN_REVISION");
		assertThat(auditados("accion = 'RESERVA_EN_REVISION' AND entidad_id = ? AND estado_anterior = 'CONFIRMADA'"
				+ " AND estado_nuevo = 'EN_REVISION'", pagando)).isEqualTo(1);

		// Un reintento con el mismo cobro no la cambia ni la vuelve a auditar
		assertThat(transacciones.confirmar(pagando, "pi_tarde").requiereDevolucion()).isFalse();
		assertThat(estado("reserva", pagando)).isEqualTo("EN_REVISION");
		assertThat(auditados("accion = 'RESERVA_EN_REVISION' AND entidad_id = ?", pagando)).isEqualTo(1);
	}

	@Test
	void unaReservaQueSePagaMientrasRestringenAlHuespedQuedaEnRevision() throws Exception {
		Usuario cliente = usuarioRepository.findByEmail("huesped@correo.com").orElseThrow();
		ReservaDTO solicitud = new ReservaDTO(null, alojamientoId, LLEGADA, SALIDA, 2, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null, null, null);
		long pagando = transacciones.iniciar(cliente, "pagando", "huella", solicitud).getId();
		api.como(administrador, post("/api/admin/usuarios/" + cliente.getId() + "/restriccion").content(MOTIVO))
				.andExpect(status().isOk());

		transacciones.confirmar(pagando, "pi_tarde");

		assertThat(estado("reserva", pagando)).isEqualTo("EN_REVISION");
	}

	@Test
	void otroRolNoPuedeModerarYElIntentoQuedaAuditado() throws Exception {
		long huespedId = usuarioId("huesped@correo.com");

		api.como(anfitrion, post("/api/admin/alojamientos/" + alojamientoId + "/bloqueo").content(MOTIVO))
				.andExpect(status().isForbidden());
		api.como(huesped, post("/api/admin/usuarios/" + usuarioId("anfitrion@correo.com") + "/restriccion")
				.content(MOTIVO)).andExpect(status().isForbidden());

		assertThat(estado("alojamiento", alojamientoId)).isEqualTo("ACTIVA");
		assertThat(estado("usuario", usuarioId("anfitrion@correo.com"))).isEqualTo("ACTIVA");
		assertThat(auditados("accion = 'ACCESO_DENEGADO' AND resultado = 'RECHAZADO'")).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT detalle FROM auditoria WHERE id > ? AND actor_id = ?"
				+ " AND actor_rol = 'HUESPED' AND accion = 'ACCESO_DENEGADO'", String.class, auditoriaDesde, huespedId))
				.startsWith("POST /api/admin/usuarios/");
	}

	@Test
	void sinMotivoNoSeBloqueaNiSeRestringe() throws Exception {
		api.como(administrador, post("/api/admin/alojamientos/" + alojamientoId + "/bloqueo").content("{}"))
				.andExpect(status().isBadRequest());
		api.como(administrador, post("/api/admin/usuarios/" + usuarioId("huesped@correo.com") + "/restriccion")
				.content("{\"motivo\":\" \"}")).andExpect(status().isBadRequest());
		api.como(administrador, post("/api/admin/alojamientos/999999/bloqueo").content(MOTIVO))
				.andExpect(status().isNotFound());
	}

	// ─────────────────────────────────────────
	// MA-81: restringir usuarios
	// ─────────────────────────────────────────

	@Test
	void alRestringirAlAnfitrionSeCierranSusSesionesYSePausanSusAlojamientos() throws Exception {
		long reserva = reservaConfirmada("r-1", LLEGADA, SALIDA);
		long anfitrionId = usuarioId("anfitrion@correo.com");
		api.como(anfitrion, get("/api/usuarios/me")).andExpect(status().isOk());

		api.como(administrador, post("/api/admin/usuarios/" + anfitrionId + "/restriccion").content(MOTIVO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("RESTRINGIDA"))
				.andExpect(jsonPath("$.alojamientosPausados").value(1))
				.andExpect(jsonPath("$.reservasEnRevision").value(1));

		// Su token deja de valer de inmediato en esta réplica y ya no puede iniciar sesión
		api.como(anfitrion, get("/api/usuarios/me")).andExpect(status().isUnauthorized());
		login("anfitrion@correo.com").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("CUENTA_RESTRINGIDA"));
		assertThat(estado("alojamiento", alojamientoId)).isEqualTo("PAUSADA");
		assertThat(estado("reserva", reserva)).isEqualTo("EN_REVISION");
		assertThat(auditados("accion IN ('RESTRINGIR_USUARIO', 'PAUSAR_ALOJAMIENTO', 'RESERVA_EN_REVISION')"))
				.isEqualTo(3);

		// Dura hasta que un administrador la levante
		api.como(administrador, delete("/api/admin/usuarios/" + anfitrionId + "/restriccion"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ACTIVA"));
		login("anfitrion@correo.com").andExpect(status().isOk());
		assertThat(estado("alojamiento", alojamientoId)).isEqualTo("PAUSADA");
	}

	@Test
	void alRestringirAlHuespedSusReservasFuturasPasanARevision() throws Exception {
		long futura = reservaConfirmada("r-1", LLEGADA, SALIDA);

		api.como(administrador, post("/api/admin/usuarios/" + usuarioId("huesped@correo.com") + "/restriccion")
				.content(MOTIVO)).andExpect(status().isOk());

		assertThat(estado("reserva", futura)).isEqualTo("EN_REVISION");
		api.como(huesped, get("/api/reservas")).andExpect(status().isUnauthorized());
	}

	@Test
	void noSePuedeRestringirAUnAdministradorNiDosVeces() throws Exception {
		api.como(administrador, post("/api/admin/usuarios/" + usuarioId("admin@correo.com") + "/restriccion")
				.content(MOTIVO)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("NO_SE_PUEDE_RESTRINGIR"));
		long huespedId = usuarioId("huesped@correo.com");
		api.como(administrador, post("/api/admin/usuarios/" + huespedId + "/restriccion").content(MOTIVO))
				.andExpect(status().isOk());
		api.como(administrador, post("/api/admin/usuarios/" + huespedId + "/restriccion").content(MOTIVO))
				.andExpect(status().isConflict());
	}

	// ─────────────────────────────────────────
	// MA-82: revisar reservas en revisión
	// ─────────────────────────────────────────

	@Test
	void elAdministradorVeLasReservasEnRevisionYDecideMantenerlasOCancelarlas() throws Exception {
		long mantenida = reservaConfirmada("r-1", LLEGADA, SALIDA);
		long cancelada = reservaConfirmada("r-2", HOY.plusDays(20), HOY.plusDays(22));
		api.como(administrador, post("/api/admin/alojamientos/" + alojamientoId + "/bloqueo").content(MOTIVO))
				.andExpect(jsonPath("$.reservasEnRevision").value(2));

		api.como(administrador, get("/api/admin/reservas/en-revision"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)));

		api.como(administrador, post("/api/admin/reservas/" + mantenida + "/mantener"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CONFIRMADA"));
		api.como(administrador, post("/api/admin/reservas/" + cancelada + "/cancelacion").content(MOTIVO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CANCELADA"))
				.andExpect(jsonPath("$.devolucionPendiente").value(false));

		assertThat(jdbc.queryForObject("SELECT valor_devolucion = total FROM reserva WHERE id = ?", Boolean.class,
				cancelada)).isTrue();
		api.como(administrador, get("/api/admin/reservas/en-revision")).andExpect(jsonPath("$", hasSize(0)));
		api.como(administrador, post("/api/admin/reservas/" + mantenida + "/mantener"))
				.andExpect(status().isConflict());
		api.como(huesped, get("/api/admin/reservas/en-revision")).andExpect(status().isForbidden());
	}

	// ─────────────────────────────────────────
	// MA-87: el registro no se puede cambiar
	// ─────────────────────────────────────────

	@Test
	void elRegistroDeAuditoriaNoSePuedeModificarNiBorrar() throws Exception {
		api.como(administrador, post("/api/admin/alojamientos/" + alojamientoId + "/bloqueo").content(MOTIVO))
				.andExpect(status().isOk());

		assertThatThrownBy(() -> jdbc.update("UPDATE auditoria SET accion = 'OTRA'"))
				.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> jdbc.update("DELETE FROM auditoria")).isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> jdbc.execute("TRUNCATE TABLE auditoria")).isInstanceOf(DataAccessException.class);
		assertThat(auditados("accion = 'BLOQUEAR_ALOJAMIENTO'")).isEqualTo(1);
	}
}
