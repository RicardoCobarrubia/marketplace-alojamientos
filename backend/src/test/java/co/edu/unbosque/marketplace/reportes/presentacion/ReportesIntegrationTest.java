package co.edu.unbosque.marketplace.reportes.presentacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reportes de gestión contra PostgreSQL real (MA-85, MA-86): el Excel exportado se compara con el
 * panel y cada exportación queda en la auditoría. Requiere Docker en ejecución.
 */
@Import({ TestcontainersConfiguration.class, PasarelaDePruebaConfig.class })
@SpringBootTest
@ActiveProfiles("test")
class ReportesIntegrationTest {

	private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));
	private static final String RUTA = "/api/admin/reportes";

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
	private ObjectMapper json;

	private ApiDePrueba api;
	private String anfitrion;
	private String huesped;
	private String administrador;
	/** Último registro de auditoría antes de la prueba: la tabla es inmutable. */
	private long auditoriaDesde;

	@BeforeEach
	void preparar() throws Exception {
		jdbc.execute("TRUNCATE TABLE evento_outbox RESTART IDENTITY");
		api = new ApiDePrueba(contexto, jdbc, passwordEncoder, cacheManager);
		pasarela.reiniciar();
		anfitrion = api.registrarEIniciarSesion("anfitrion@correo.com", "ANFITRION");
		huesped = api.registrarEIniciarSesion("huesped@correo.com", "HUESPED");
		administrador = api.administrador();
		long alojamiento = api.crearAlojamientoActivo(anfitrion, administrador);
		api.como(anfitrion, post("/api/alojamientos/" + alojamiento + "/periodos")
				.content("{\"fechaInicio\":\"%s\",\"fechaFin\":\"%s\"}".formatted(HOY, HOY.plusDays(60))))
				.andExpect(status().isCreated());
		for (int i = 0; i < 2; i++) {
			api.como(huesped, post("/api/reservas").header("Idempotency-Key", "r-" + i).content("""
					{"alojamientoId":%d,"llegada":"%s","salida":"%s","huespedes":2}"""
					.formatted(alojamiento, HOY.plusDays(5 + 10L * i), HOY.plusDays(8 + 10L * i))))
					.andExpect(status().isCreated());
		}
		Long ultimo = jdbc.queryForObject("SELECT max(id) FROM auditoria", Long.class);
		auditoriaDesde = ultimo == null ? 0 : ultimo;
	}

	private JsonNode panel(String ruta) throws Exception {
		return json.readTree(api.como(administrador, get(ruta)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString());
	}

	private static Map<String, Double> resumen(Sheet hoja) {
		Map<String, Double> valores = new HashMap<>();
		for (Row fila : hoja) {
			if (fila.getRowNum() > 0) {
				valores.put(fila.getCell(0).getStringCellValue(), fila.getCell(1).getNumericCellValue());
			}
		}
		return valores;
	}

	@Test
	void elExcelTraeTodasLasSeccionesYCoincideConElPanel() throws Exception {
		byte[] archivo = api.como(administrador, get(RUTA).param("formato", "EXCEL"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", containsString("spreadsheetml")))
				.andExpect(header().string("Content-Disposition", containsString(".xlsx")))
				.andReturn().getResponse().getContentAsByteArray();
		JsonNode indicadores = panel("/api/admin/panel/indicadores");
		JsonNode ingresos = panel("/api/admin/panel/ingresos");

		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
			assertThat(libro.getSheet("Usuarios registrados").getLastRowNum()).isEqualTo(3);
			assertThat(libro.getSheet("Alojamientos creados").getLastRowNum()).isEqualTo(1);
			Sheet reservas = libro.getSheet("Reservas");
			assertThat(reservas.getLastRowNum()).isEqualTo(2);
			// Todas las columnas llegan, también las de nombre compuesto
			Row reserva = reservas.getRow(1);
			assertThat(reserva.getCell(1).getNumericCellValue()).isPositive();
			assertThat(reserva.getCell(3).getNumericCellValue()).isPositive();
			assertThat(reserva.getCell(8).getNumericCellValue()).isPositive();
			assertThat(reserva.getCell(9).getStringCellValue()).startsWith(HOY.toString().substring(0, 4));
			Row alojamientoCreado = libro.getSheet("Alojamientos creados").getRow(1);
			assertThat(alojamientoCreado.getCell(4).getNumericCellValue()).isPositive();
			assertThat(alojamientoCreado.getCell(6).getNumericCellValue()).isEqualTo(200000);
			Sheet demanda = libro.getSheet("Demanda por ciudad y tipo");
			assertThat(demanda.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Medellín");
			assertThat(demanda.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(2);

			// MA-85, escenario 2: las cifras coinciden con el panel
			Map<String, Double> resumen = resumen(libro.getSheet("Resumen"));
			assertThat(resumen.get("Usuarios")).isEqualTo(indicadores.get("usuarios").asDouble());
			assertThat(resumen.get("Alojamientos activos")).isEqualTo(indicadores.get("alojamientosActivos").asDouble());
			assertThat(resumen.get("Reservas CONFIRMADA"))
					.isEqualTo(indicadores.get("reservasPorEstado").get("CONFIRMADA").asDouble());
			assertThat(resumen.get("Ingresos estimados del período"))
					.isEqualTo(ingresos.get("ingresosEstimados").asDouble()).isPositive();
			assertThat(demanda.getRow(1).getCell(4).getNumericCellValue())
					.isEqualTo(ingresos.get("ingresosEstimados").asDouble());
		}
	}

	@Test
	void cadaExportacionQuedaEnLaAuditoriaConQuienLaPidio() throws Exception {
		byte[] pdf = api.como(administrador, get(RUTA).param("formato", "PDF")
				.param("desde", HOY.minusDays(6).toString()).param("hasta", HOY.toString()))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/pdf"))
				.andReturn().getResponse().getContentAsByteArray();
		assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

		Long adminId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'admin@correo.com'", Long.class);
		assertThat(jdbc.queryForList("SELECT estado_nuevo FROM auditoria WHERE id > ? AND accion = 'EXPORTAR_REPORTE'"
				+ " AND actor_id = ? AND actor_rol = 'ADMINISTRADOR' ORDER BY id", String.class, auditoriaDesde, adminId))
				.containsExactly("SOLICITADO", "GENERADO");

		// MA-86, escenario 3: aparece al consultar la auditoría
		api.como(administrador, get("/api/admin/auditoria").param("accion", "EXPORTAR_REPORTE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].estadoNuevo").value("GENERADO"))
				.andExpect(jsonPath("$[0].actorId").value(adminId))
				.andExpect(jsonPath("$[1].estadoNuevo").value("SOLICITADO"))
				.andExpect(jsonPath("$[1].detalle").value("PDF del %s al %s".formatted(HOY.minusDays(6), HOY)));
	}

	@Test
	void otroRolNoExportaYLosDatosInvalidosSeRechazan() throws Exception {
		api.como(huesped, get(RUTA).param("formato", "PDF")).andExpect(status().isForbidden());
		api.como(anfitrion, get(RUTA).param("formato", "EXCEL")).andExpect(status().isForbidden());
		api.como(administrador, get(RUTA).param("formato", "PDF").param("desde", HOY.minusYears(2).toString())
				.param("hasta", HOY.toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("RANGO_DEMASIADO_LARGO"));
		api.como(administrador, get(RUTA).param("formato", "WORD")).andExpect(status().isBadRequest());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE id > ? AND accion = 'EXPORTAR_REPORTE'",
				Integer.class, auditoriaDesde)).isZero();
	}
}
