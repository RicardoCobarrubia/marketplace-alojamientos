package co.edu.unbosque.marketplace.panel.presentacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

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
import co.edu.unbosque.marketplace.reservas.presentacion.PasarelaDePrueba;
import co.edu.unbosque.marketplace.reservas.presentacion.PasarelaDePruebaConfig;

/**
 * Panel del administrador contra PostgreSQL real (MA-83, MA-84): las cifras se comparan con
 * consultas directas a la base de datos (MA-84, escenario 3). Requiere Docker en ejecución.
 */
@Import({ TestcontainersConfiguration.class, PasarelaDePruebaConfig.class })
@SpringBootTest
@ActiveProfiles("test")
class PanelIntegrationTest {

	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
	private static final LocalDate HOY = LocalDate.now(BOGOTA);
	private static final long MEDELLIN = 2;
	private static final long CARTAGENA = 3;

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
	private String anfitrion;
	private String huesped;
	private String administrador;

	@BeforeEach
	void preparar() throws Exception {
		jdbc.execute("TRUNCATE TABLE evento_outbox RESTART IDENTITY");
		api = new ApiDePrueba(contexto, jdbc, passwordEncoder, cacheManager);
		pasarela.reiniciar();
		anfitrion = api.registrarEIniciarSesion("anfitrion@correo.com", "ANFITRION");
		huesped = api.registrarEIniciarSesion("huesped@correo.com", "HUESPED");
		administrador = api.administrador();
	}

	private long alojamientoEn(long ciudadId) throws Exception {
		long id = api.crearAlojamientoActivo(anfitrion, administrador);
		jdbc.update("UPDATE alojamiento SET ciudad_id = ? WHERE id = ?", ciudadId, id);
		api.como(anfitrion, post("/api/alojamientos/" + id + "/periodos")
				.content("{\"fechaInicio\":\"%s\",\"fechaFin\":\"%s\"}".formatted(HOY, HOY.plusDays(60))))
				.andExpect(status().isCreated());
		return id;
	}

	private long reservar(long alojamientoId, String clave, int desde) throws Exception {
		return ApiDePrueba.id(api.como(huesped, post("/api/reservas").header("Idempotency-Key", clave).content("""
				{"alojamientoId":%d,"llegada":"%s","salida":"%s","huespedes":2}"""
				.formatted(alojamientoId, HOY.plusDays(desde), HOY.plusDays(desde + 3))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	/** Lo que dice la base de datos, calculado aparte del panel. */
	private BigDecimal tarifasDesde(Instant desde) {
		return jdbc.queryForObject("SELECT coalesce(sum(tarifa_servicio), 0) FROM reserva"
				+ " WHERE estado IN ('CONFIRMADA', 'COMPLETADA') AND creado_en >= ?", BigDecimal.class,
				Timestamp.from(desde));
	}

	@Test
	void elAdministradorVeLosIndicadoresYOtroRolNoEntra() throws Exception {
		long alojamiento = alojamientoEn(MEDELLIN);
		alojamientoEn(CARTAGENA);
		reservar(alojamiento, "r-1", 5);
		reservar(alojamiento, "r-2", 10);

		api.como(administrador, get("/api/admin/panel/indicadores"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usuarios").value(3))
				.andExpect(jsonPath("$.usuariosPorRol.HUESPED").value(1))
				.andExpect(jsonPath("$.usuariosPorRol.ANFITRION").value(1))
				.andExpect(jsonPath("$.usuariosPorRol.ADMINISTRADOR").value(1))
				.andExpect(jsonPath("$.alojamientosActivos").value(2))
				.andExpect(jsonPath("$.reservas").value(2))
				.andExpect(jsonPath("$.reservasPorEstado.CONFIRMADA").value(2))
				.andExpect(jsonPath("$.reservasPorEstado.CANCELADA").value(0));

		for (String ruta : new String[] { "/api/admin/panel/indicadores", "/api/admin/panel/ingresos" }) {
			api.como(huesped, get(ruta)).andExpect(status().isForbidden());
			api.como(anfitrion, get(ruta)).andExpect(status().isForbidden());
		}
	}

	@Test
	void losIngresosYLasCiudadesCoincidenConLaBaseDeDatos() throws Exception {
		long medellin = alojamientoEn(MEDELLIN);
		long cartagena = alojamientoEn(CARTAGENA);
		reservar(medellin, "r-1", 5);
		reservar(medellin, "r-2", 10);
		long antigua = reservar(medellin, "r-3", 15);
		reservar(cartagena, "r-4", 5);
		// Una reserva hecha hace 40 días queda fuera del rango por defecto
		Instant haceCuarentaDias = Instant.now().minus(40, ChronoUnit.DAYS);
		jdbc.update("UPDATE reserva SET creado_en = ? WHERE id = ?", Timestamp.from(haceCuarentaDias), antigua);

		Instant inicioPorDefecto = HOY.minusDays(29).atStartOfDay(BOGOTA).toInstant();
		BigDecimal esperado = tarifasDesde(inicioPorDefecto);
		assertThat(esperado).isPositive();

		api.como(administrador, get("/api/admin/panel/ingresos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.desde").value(HOY.minusDays(29).toString()))
				.andExpect(jsonPath("$.hasta").value(HOY.toString()))
				.andExpect(jsonPath("$.ingresosEstimados").value(esperado.doubleValue()))
				.andExpect(jsonPath("$.reservas").value(3))
				.andExpect(jsonPath("$.ciudadesConMasReservas[0].ciudad").value("Medellín"))
				.andExpect(jsonPath("$.ciudadesConMasReservas[0].reservas").value(2))
				.andExpect(jsonPath("$.ciudadesConMasReservas[1].ciudad").value("Cartagena"))
				.andExpect(jsonPath("$.ciudadesConMasReservas[1].reservas").value(1));

		// Con un rango que la incluye, cuenta también la de hace 40 días
		String desde = HOY.minusDays(45).toString();
		api.como(administrador, get("/api/admin/panel/ingresos").param("desde", desde).param("hasta", HOY.toString()))
				.andExpect(jsonPath("$.ingresosEstimados")
						.value(tarifasDesde(HOY.minusDays(45).atStartOfDay(BOGOTA).toInstant()).doubleValue()))
				.andExpect(jsonPath("$.reservas").value(4))
				.andExpect(jsonPath("$.ciudadesConMasReservas[0].reservas").value(3));

		// Las canceladas no suman
		jdbc.update("UPDATE reserva SET estado = 'CANCELADA', motivo_cancelacion = 'Prueba', cancelada_por = huesped_id,"
				+ " cancelada_en = now(), valor_devolucion = 0 WHERE alojamiento_id = ?", cartagena);
		api.como(administrador, get("/api/admin/panel/ingresos"))
				.andExpect(jsonPath("$.reservas").value(2))
				.andExpect(jsonPath("$.ingresosEstimados").value(tarifasDesde(inicioPorDefecto).doubleValue()))
				.andExpect(jsonPath("$.ciudadesConMasReservas.length()").value(1));

		api.como(administrador, get("/api/admin/panel/ingresos").param("desde", HOY.toString())
				.param("hasta", HOY.minusDays(1).toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("RANGO_INVALIDO"));
	}
}
