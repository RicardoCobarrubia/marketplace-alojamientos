package co.edu.unbosque.marketplace.auditoria.presentacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

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
import org.springframework.web.context.WebApplicationContext;

import co.edu.unbosque.marketplace.ApiDePrueba;
import co.edu.unbosque.marketplace.TestcontainersConfiguration;
import co.edu.unbosque.marketplace.reservas.presentacion.PasarelaDePrueba;
import co.edu.unbosque.marketplace.reservas.presentacion.PasarelaDePruebaConfig;

/**
 * Registro de auditoría de los eventos relevantes contra PostgreSQL real (MA-87).
 * Requiere Docker en ejecución.
 */
@Import({ TestcontainersConfiguration.class, PasarelaDePruebaConfig.class })
@SpringBootTest
@ActiveProfiles("test")
class AuditoriaIntegrationTest {

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

	private ApiDePrueba api;
	private long desde;

	@BeforeEach
	void preparar() {
		api = new ApiDePrueba(contexto, jdbc, passwordEncoder, cacheManager);
		pasarela.reiniciar();
		// La auditoría no se puede vaciar: cada prueba solo mira los registros que ella crea
		Long ultimo = jdbc.queryForObject("SELECT max(id) FROM auditoria", Long.class);
		desde = ultimo == null ? 0 : ultimo;
	}

	private List<String> acciones() {
		return jdbc.queryForList("SELECT accion FROM auditoria WHERE id > ? ORDER BY id", String.class, desde);
	}

	private int auditados(String condicion, Object... parametros) {
		Object[] conDesde = new Object[parametros.length + 1];
		conDesde[0] = desde;
		System.arraycopy(parametros, 0, conDesde, 1, parametros.length);
		Integer cantidad = jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE id > ? AND " + condicion,
				Integer.class, conDesde);
		return cantidad == null ? 0 : cantidad;
	}

	private long usuarioId(String email) {
		return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
	}

	@Test
	void elRegistroYLosIniciosDeSesionQuedanAuditadosSinLaContrasena() throws Exception {
		api.registrarEIniciarSesion("huesped@correo.com", "HUESPED");
		long huesped = usuarioId("huesped@correo.com");
		api.mvc().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"huesped@correo.com\",\"password\":\"otra-clave-mala\"}"))
				.andExpect(status().isUnauthorized());
		api.mvc().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nadie@correo.com\",\"password\":\"otra-clave-mala\"}"))
				.andExpect(status().isUnauthorized());

		assertThat(auditados("accion = 'REGISTRO_USUARIO' AND actor_id = ? AND entidad_id = ? AND estado_nuevo = 'ACTIVA'",
				huesped, huesped)).isEqualTo(1);
		assertThat(auditados("accion = 'INICIO_SESION' AND resultado = 'EXITO' AND actor_id = ? AND actor_rol = 'HUESPED'",
				huesped)).isEqualTo(1);
		assertThat(auditados("accion = 'INICIO_SESION' AND resultado = 'RECHAZADO' AND actor_id = ?"
				+ " AND detalle = 'Credenciales inválidas'", huesped)).isEqualTo(1);
		// Con un correo que no existe no se guarda cuál se intentó
		assertThat(auditados("accion = 'INICIO_SESION' AND resultado = 'RECHAZADO' AND actor_id IS NULL")).isEqualTo(1);
		assertThat(auditados("detalle LIKE ? OR detalle LIKE ? OR detalle LIKE ?", "%" + ApiDePrueba.CLAVE + "%",
				"%otra-clave-mala%", "%nadie@correo.com%")).isZero();
	}

	@Test
	void elCicloDelAlojamientoYDeLaReservaQuedaAuditado() throws Exception {
		String anfitrion = api.registrarEIniciarSesion("anfitrion@correo.com", "ANFITRION");
		String huesped = api.registrarEIniciarSesion("huesped@correo.com", "HUESPED");
		String administrador = api.administrador();
		long alojamientoId = api.crearAlojamientoActivo(anfitrion, administrador);
		api.como(anfitrion, post("/api/alojamientos/" + alojamientoId + "/periodos")
				.content("{\"fechaInicio\":\"%s\",\"fechaFin\":\"%s\"}".formatted(HOY, HOY.plusDays(60))))
				.andExpect(status().isCreated());
		long reserva = ApiDePrueba.id(api.como(huesped, post("/api/reservas").header("Idempotency-Key", "r-1")
				.content("""
						{"alojamientoId":%d,"llegada":"%s","salida":"%s","huespedes":2}"""
						.formatted(alojamientoId, HOY.plusDays(10), HOY.plusDays(13))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		api.como(huesped, post("/api/reservas/" + reserva + "/cancelacion").content("{\"motivo\":\"Cambio de planes\"}"))
				.andExpect(status().isOk());

		assertThat(acciones()).contains("CREAR_ALOJAMIENTO", "ENVIAR_A_REVISION", "APROBAR_ALOJAMIENTO",
				"RESERVA_CONFIRMADA", "RESERVA_CANCELADA", "DEVOLUCION_SOLICITADA");
		long admin = usuarioId("admin@correo.com");
		assertThat(auditados("accion = 'APROBAR_ALOJAMIENTO' AND actor_id = ? AND estado_anterior = 'PENDIENTE_REVISION'"
				+ " AND estado_nuevo = 'ACTIVA'", admin)).isEqualTo(1);
		assertThat(auditados("accion = 'RESERVA_CANCELADA' AND entidad = 'RESERVA' AND entidad_id = ?"
				+ " AND estado_anterior = 'CONFIRMADA' AND estado_nuevo = 'CANCELADA' AND detalle = 'Cambio de planes'"
				+ " AND actor_id = ? AND correlation_id IS NOT NULL", reserva, usuarioId("huesped@correo.com")))
				.isEqualTo(1);

		// El administrador lo consulta por entidad; otro rol no puede
		api.como(administrador, get("/api/admin/auditoria").param("entidad", "RESERVA")
				.param("entidadId", String.valueOf(reserva)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].accion", hasItems("RESERVA_CONFIRMADA", "RESERVA_CANCELADA")))
				.andExpect(jsonPath("$[0].accion").value("DEVOLUCION_SOLICITADA"));
		api.como(huesped, get("/api/admin/auditoria")).andExpect(status().isForbidden());
		api.como(administrador, get("/api/admin/auditoria").param("entidad", "RESERVA"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void lasAccionesAdministrativasQuedanAuditadas() throws Exception {
		String administrador = api.administrador();
		long admin = usuarioId("admin@correo.com");

		api.como(administrador, post("/api/admin/catalogos/ciudades")
				.content("{\"nombre\":\"Villa de Leyva\",\"departamento\":\"Boyacá\"}"))
				.andExpect(status().isCreated());
		try {
			api.como(administrador, put("/api/admin/configuracion/cotizacion").content("""
					{"porcentajeTarifaServicio":12,"porcentajeDescuentoEstadiaLarga":10,"minimoNochesDescuento":7}"""))
					.andExpect(status().isOk());
		} finally {
			// Las demás pruebas cuentan con la configuración por defecto
			api.como(administrador, put("/api/admin/configuracion/cotizacion").content("""
					{"porcentajeTarifaServicio":10,"porcentajeDescuentoEstadiaLarga":10,"minimoNochesDescuento":7}"""))
					.andExpect(status().isOk());
		}

		assertThat(auditados("accion = 'CREAR_CIUDAD' AND actor_id = ? AND detalle = 'Villa de Leyva'", admin))
				.isEqualTo(1);
		assertThat(auditados("accion = 'ACTUALIZAR_CONFIGURACION_COTIZACION' AND actor_id = ?"
				+ " AND detalle LIKE '%Ahora: tarifa 12%'", admin)).isEqualTo(1);
		api.como(administrador, get("/api/admin/auditoria").param("actorId", String.valueOf(admin)))
				.andExpect(jsonPath("$[0].accion").value("ACTUALIZAR_CONFIGURACION_COTIZACION"));
	}
}
