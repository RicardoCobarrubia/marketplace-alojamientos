package co.edu.unbosque.marketplace.cotizacion.presentacion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import co.edu.unbosque.marketplace.ApiDePrueba;
import co.edu.unbosque.marketplace.TestcontainersConfiguration;

/** Cotización contra PostgreSQL real (MA-63, MA-64, MA-65). Requiere Docker en ejecución. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class CotizacionIntegrationTest {

	private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));
	private static final String CONFIGURACION = "/api/admin/configuracion/cotizacion";

	@Autowired
	private WebApplicationContext contexto;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private CacheManager cacheManager;

	private ApiDePrueba api;
	private String anfitrion;
	private String administrador;
	private long alojamientoId;

	@BeforeEach
	void preparar() throws Exception {
		api = new ApiDePrueba(contexto, jdbc, passwordEncoder, cacheManager);
		anfitrion = api.registrarEIniciarSesion("anfitrion@correo.com", "ANFITRION");
		administrador = api.administrador();
		alojamientoId = api.crearAlojamientoActivo(anfitrion, administrador);
		// La configuración vuelve a los valores de la migración en cada prueba
		jdbc.update("UPDATE configuracion_cotizacion SET porcentaje_tarifa_servicio = 10, "
				+ "porcentaje_descuento_estadia_larga = 10, minimo_noches_descuento = 7, actualizado_por = NULL");
	}

	private ResultActions cotizar(LocalDate llegada, LocalDate salida, String servicios) throws Exception {
		var peticion = get("/api/alojamientos/" + alojamientoId + "/cotizacion")
				.param("llegada", llegada.toString()).param("salida", salida.toString());
		if (servicios != null) {
			peticion.param("servicios", servicios);
		}
		return api.mvc().perform(peticion);
	}

	@Test
	void cualquieraCotizaConElDesgloseCompleto() throws Exception {
		long desayuno = api.crearServicioAdicional(anfitrion, alojamientoId, "Desayuno", 20000, "POR_NOCHE");

		// 7 noches × 200.000 = 1.400.000, 10 % de descuento, limpieza 30.000, desayuno 20.000 × 7
		cotizar(HOY.plusDays(10), HOY.plusDays(17), String.valueOf(desayuno))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.noches").value(7))
				.andExpect(jsonPath("$.subtotal").value(1400000.0))
				.andExpect(jsonPath("$.totalDescuentos").value(140000.0))
				.andExpect(jsonPath("$.limpieza").value(30000.0))
				.andExpect(jsonPath("$.servicios[0].cantidad").value(7))
				.andExpect(jsonPath("$.totalServicios").value(140000.0))
				// (1.400.000 - 140.000 + 30.000 + 140.000) × 1,10
				.andExpect(jsonPath("$.tarifaServicio").value(143000.0))
				.andExpect(jsonPath("$.total").value(1573000.0));
	}

	@Test
	void rechazaFechasInvalidasYServiciosAjenos() throws Exception {
		cotizar(HOY.minusDays(1), HOY.plusDays(2), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("FECHAS_INVALIDAS"));
		cotizar(HOY.plusDays(2), HOY.plusDays(1), null).andExpect(status().isBadRequest());
		cotizar(HOY.plusDays(1), HOY.plusDays(2), "999")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("SERVICIO_NO_PERTENECE"));
	}

	@Test
	void elCambioDelAdministradorSeAplicaALaSiguienteCotizacion() throws Exception {
		cotizar(HOY.plusDays(1), HOY.plusDays(4), null).andExpect(jsonPath("$.tarifaServicio").value(63000.0));

		api.como(administrador, put(CONFIGURACION).content("""
				{"porcentajeTarifaServicio":5,"porcentajeDescuentoEstadiaLarga":20,"minimoNochesDescuento":3}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.actualizadoEn").exists());

		// 3 noches ya tienen descuento: (600.000 - 120.000 + 30.000) × 5 %
		cotizar(HOY.plusDays(1), HOY.plusDays(4), null)
				.andExpect(jsonPath("$.totalDescuentos").value(120000.0))
				.andExpect(jsonPath("$.tarifaServicio").value(25500.0));
	}

	@Test
	void soloElAdministradorConfiguraYConValoresValidos() throws Exception {
		api.como(anfitrion, put(CONFIGURACION).content("""
				{"porcentajeTarifaServicio":5,"porcentajeDescuentoEstadiaLarga":20,"minimoNochesDescuento":3}"""))
				.andExpect(status().isForbidden());
		api.como(anfitrion, get(CONFIGURACION)).andExpect(status().isForbidden());

		api.como(administrador, put(CONFIGURACION).content("""
				{"porcentajeTarifaServicio":120,"porcentajeDescuentoEstadiaLarga":-1,"minimoNochesDescuento":0}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.porcentajeTarifaServicio").exists())
				.andExpect(jsonPath("$.errores.porcentajeDescuentoEstadiaLarga").exists())
				.andExpect(jsonPath("$.errores.minimoNochesDescuento").exists());
		api.como(administrador, get(CONFIGURACION))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.porcentajeTarifaServicio").value(10.0));
	}
}
