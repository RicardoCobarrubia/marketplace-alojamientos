package co.edu.unbosque.marketplace.perfiles.presentacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import co.edu.unbosque.marketplace.ApiDePrueba;
import co.edu.unbosque.marketplace.TestcontainersConfiguration;
import co.edu.unbosque.marketplace.comun.excepcion.AccesoDenegadoException;
import co.edu.unbosque.marketplace.reservas.aplicacion.ReservaDTO;
import co.edu.unbosque.marketplace.reservas.aplicacion.ReservaTransacciones;
import co.edu.unbosque.marketplace.reservas.presentacion.PasarelaDePrueba;
import co.edu.unbosque.marketplace.reservas.presentacion.PasarelaDePruebaConfig;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.infraestructura.UsuarioRepository;

/** Perfil y estado de la cuenta contra PostgreSQL real (MA-51, MA-52). Requiere Docker en ejecución. */
@Import({ TestcontainersConfiguration.class, PasarelaDePruebaConfig.class })
@SpringBootTest
@ActiveProfiles("test")
class PerfilesIntegrationTest {

	private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));
	private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
	private static final String CREDENCIALES = "{\"email\":\"%s\",\"password\":\"%s\"}";

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
	/** Último registro de auditoría antes de la prueba: la tabla es inmutable y los ids se reinician. */
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
		Long ultimo = jdbc.queryForObject("SELECT max(id) FROM auditoria", Long.class);
		auditoriaDesde = ultimo == null ? 0 : ultimo;
	}

	private ResultActions sinToken(String ruta, String cuerpo) throws Exception {
		return api.mvc().perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private static String token(ResultActions respuesta) throws Exception {
		Matcher token = TOKEN.matcher(respuesta.andReturn().getResponse().getContentAsString());
		assertThat(token.find()).isTrue();
		return token.group(1);
	}

	private void reservar(String clave, int desde) throws Exception {
		api.como(huesped, post("/api/reservas").header("Idempotency-Key", clave).content("""
				{"alojamientoId":%d,"llegada":"%s","salida":"%s","huespedes":2}"""
				.formatted(alojamientoId, HOY.plusDays(desde), HOY.plusDays(desde + 3))))
				.andExpect(status().isCreated());
	}

	private String estadoCuenta(String email) {
		return jdbc.queryForObject("SELECT estado_cuenta FROM usuario WHERE email = ?", String.class, email);
	}

	private String estadoAlojamiento() {
		return jdbc.queryForObject("SELECT estado FROM alojamiento WHERE id = ?", String.class, alojamientoId);
	}

	private int auditados(String accion) {
		Integer total = jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE id > ? AND accion = ?",
				Integer.class, auditoriaDesde, accion);
		return total == null ? 0 : total;
	}

	// ─────────────────────────────────────────
	// MA-51: consultar y actualizar el perfil
	// ─────────────────────────────────────────

	@Test
	void cadaUsuarioConsultaYCambiaSoloSuNombreYSuTelefono() throws Exception {
		api.como(huesped, get("/api/usuarios/me"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("huesped@correo.com"))
				.andExpect(jsonPath("$.password").doesNotExist());

		api.como(huesped, put("/api/usuarios/me").content("""
				{"nombre":"Ana María","telefono":"3109876543","email":"huesped@correo.com","rol":"HUESPED"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Ana María"))
				.andExpect(jsonPath("$.telefono").value("3109876543"));
		api.como(huesped, get("/api/usuarios/me")).andExpect(jsonPath("$.nombre").value("Ana María"));

		// El correo y el rol no se editan, y los datos se validan
		api.como(huesped, put("/api/usuarios/me").content("""
				{"nombre":"Ana","telefono":"3109876543","email":"otro@correo.com"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CAMPO_NO_EDITABLE"));
		api.como(huesped, put("/api/usuarios/me").content("""
				{"nombre":"Ana","telefono":"3109876543","rol":"ADMINISTRADOR"}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CAMPO_NO_EDITABLE"));
		api.como(huesped, put("/api/usuarios/me").content("{\"nombre\":\"\",\"telefono\":\"abc\"}"))
				.andExpect(status().isBadRequest());
		api.mvc().perform(put("/api/usuarios/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Intruso\",\"telefono\":\"3000000000\"}")).andExpect(status().isUnauthorized());

		// El perfil del anfitrión no cambió
		api.como(anfitrion, get("/api/usuarios/me")).andExpect(jsonPath("$.nombre").value("Usuario"));
		assertThat(jdbc.queryForObject("SELECT rol FROM usuario WHERE email = 'huesped@correo.com'", String.class))
				.isEqualTo("HUESPED");
		assertThat(auditados("ACTUALIZAR_PERFIL")).isEqualTo(1);
	}

	// ─────────────────────────────────────────
	// MA-52: desactivar y reactivar la cuenta
	// ─────────────────────────────────────────

	@Test
	void elAnfitrionDesactivaSuCuentaYLaReactivaAlIniciarSesionYConfirmar() throws Exception {
		api.como(anfitrion, post("/api/usuarios/me/desactivacion")).andExpect(status().isNoContent());

		assertThat(estadoCuenta("anfitrion@correo.com")).isEqualTo("DESACTIVADA");
		assertThat(estadoAlojamiento()).isEqualTo("PAUSADA");
		// Sus sesiones se cierran: el token ya no sirve
		api.como(anfitrion, get("/api/usuarios/me")).andExpect(status().isUnauthorized());
		// Al iniciar sesión se le avisa que puede reactivarla
		sinToken("/api/auth/login", CREDENCIALES.formatted("anfitrion@correo.com", ApiDePrueba.CLAVE))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("CUENTA_DESACTIVADA"));
		sinToken("/api/auth/reactivacion", CREDENCIALES.formatted("anfitrion@correo.com", "clave-incorrecta"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
		assertThat(estadoCuenta("anfitrion@correo.com")).isEqualTo("DESACTIVADA");

		String nuevo = token(sinToken("/api/auth/reactivacion",
				CREDENCIALES.formatted("anfitrion@correo.com", ApiDePrueba.CLAVE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usuario.estadoCuenta").value("ACTIVA")));

		api.como(nuevo, get("/api/usuarios/me")).andExpect(status().isOk());
		// El token de antes de desactivarla sigue sin servir, y los alojamientos esperan a que los reanude
		api.como(anfitrion, get("/api/usuarios/me")).andExpect(status().isUnauthorized());
		assertThat(estadoAlojamiento()).isEqualTo("PAUSADA");
		assertThat(auditados("DESACTIVAR_CUENTA")).isEqualTo(1);
		assertThat(auditados("REACTIVAR_CUENTA")).isEqualTo(1);
		assertThat(auditados("PAUSAR_ALOJAMIENTO")).isEqualTo(1);
	}

	@Test
	void conReservasConfirmadasSinTerminarNoSePuedeDesactivar() throws Exception {
		reservar("r-1", 10);

		for (String token : new String[] { huesped, anfitrion }) {
			api.como(token, post("/api/usuarios/me/desactivacion"))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.codigo").value("RESERVAS_VIGENTES"));
		}

		// No queda nada a medias
		assertThat(estadoCuenta("huesped@correo.com")).isEqualTo("ACTIVA");
		assertThat(estadoCuenta("anfitrion@correo.com")).isEqualTo("ACTIVA");
		assertThat(estadoAlojamiento()).isEqualTo("ACTIVA");
		api.como(huesped, get("/api/usuarios/me")).andExpect(status().isOk());
		assertThat(auditados("PAUSAR_ALOJAMIENTO")).isZero();

		// Una estadía que ya terminó no lo impide
		jdbc.update("UPDATE reserva SET fecha_llegada = ?, fecha_salida = ?", HOY.minusDays(5), HOY.minusDays(1));
		api.como(huesped, post("/api/usuarios/me/desactivacion")).andExpect(status().isNoContent());
	}

	@Test
	void conUnTokenQueTodaviaNoVenceUnaCuentaDesactivadaNoPuedeReservar() throws Exception {
		Usuario cliente = usuarioRepository.findByEmail("huesped@correo.com").orElseThrow();
		api.como(huesped, post("/api/usuarios/me/desactivacion")).andExpect(status().isNoContent());
		ReservaDTO solicitud = new ReservaDTO(null, alojamientoId, HOY.plusDays(10), HOY.plusDays(13), 2, null, null,
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

		// Otra réplica todavía tiene la cuenta en su caché de 60 s y le deja pasar la petición
		assertThatThrownBy(() -> transacciones.iniciar(cliente, "otra-replica", "huella", solicitud))
				.isInstanceOf(AccesoDenegadoException.class);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva", Integer.class)).isZero();
	}

	@Test
	void elAdministradorNoDesactivaSuCuenta() throws Exception {
		api.como(administrador, post("/api/usuarios/me/desactivacion")).andExpect(status().isForbidden());
		assertThat(estadoCuenta("admin@correo.com")).isEqualTo("ACTIVA");
	}
}
