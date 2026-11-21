package co.edu.unbosque.marketplace.busqueda.presentacion;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;

import co.edu.unbosque.marketplace.ApiDePrueba;
import co.edu.unbosque.marketplace.TestcontainersConfiguration;
import co.edu.unbosque.marketplace.reservas.presentacion.PasarelaDePrueba;
import co.edu.unbosque.marketplace.reservas.presentacion.PasarelaDePruebaConfig;

/**
 * Búsqueda de alojamientos contra PostgreSQL real (MA-61). Los alojamientos de prueba son de
 * Medellín (2), capacidad 4, 200.000 por noche, tipo 3 y con wifi (servicio 1); cada prueba cambia
 * lo que necesita. Requiere Docker en ejecución.
 */
@Import({ TestcontainersConfiguration.class, PasarelaDePruebaConfig.class })
@SpringBootTest
@ActiveProfiles("test")
class BusquedaIntegrationTest {

	private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));
	private static final String RUTA = "/api/alojamientos";

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

	private long alojamiento(String titulo) throws Exception {
		long id = api.crearAlojamientoActivo(anfitrion, administrador);
		jdbc.update("UPDATE alojamiento SET titulo = ? WHERE id = ?", titulo, id);
		return id;
	}

	private void disponible(long alojamientoId) throws Exception {
		api.como(anfitrion, post("/api/alojamientos/" + alojamientoId + "/periodos")
				.content("{\"fechaInicio\":\"%s\",\"fechaFin\":\"%s\"}".formatted(HOY, HOY.plusDays(60))))
				.andExpect(status().isCreated());
	}

	/** Sin token: cualquiera puede buscar. */
	private ResultActions buscar(MockHttpServletRequestBuilder peticion) throws Exception {
		return api.mvc().perform(peticion);
	}

	@Test
	void soloAparecenLosActivosDeLaCiudadYSinTokenTambienSeBusca() throws Exception {
		alojamiento("Cabaña activa");
		long pausada = alojamiento("Cabaña pausada");
		long cartagena = alojamiento("Casa en Cartagena");
		jdbc.update("UPDATE alojamiento SET estado = 'PAUSADA' WHERE id = ?", pausada);
		jdbc.update("UPDATE alojamiento SET ciudad_id = 3 WHERE id = ?", cartagena);

		buscar(get(RUTA).param("ciudadId", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.alojamientos[*].titulo", contains("Cabaña activa")))
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.mensaje").doesNotExist());
		buscar(get(RUTA).param("ciudadId", "4"))
				.andExpect(jsonPath("$.alojamientos", empty()))
				.andExpect(jsonPath("$.mensaje", containsString("No encontramos alojamientos")));
		// Sin filtros: todos los publicados, de cualquier ciudad
		buscar(get(RUTA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.alojamientos[*].titulo", contains("Cabaña activa", "Casa en Cartagena")))
				.andExpect(jsonPath("$.total").value(2));
	}

	@Test
	void losResultadosCumplenTodosLosFiltros() throws Exception {
		alojamiento("Básica");
		long grande = alojamiento("Grande");
		long apartamento = alojamiento("Apartamento");
		jdbc.update("UPDATE alojamiento SET capacidad = 8, precio_noche = 450000 WHERE id = ?", grande);
		jdbc.update("INSERT INTO alojamiento_servicio (alojamiento_id, servicio_id) VALUES (?, 2)", grande);
		jdbc.update("UPDATE alojamiento SET tipo_id = 1, precio_noche = 150000 WHERE id = ?", apartamento);

		buscar(get(RUTA).param("ciudadId", "2"))
				.andExpect(jsonPath("$.alojamientos[*].titulo", contains("Apartamento", "Básica", "Grande")));
		buscar(get(RUTA).param("ciudadId", "2").param("huespedes", "6"))
				.andExpect(jsonPath("$.alojamientos[*].titulo", contains("Grande")));
		buscar(get(RUTA).param("ciudadId", "2").param("tipoId", "1"))
				.andExpect(jsonPath("$.alojamientos[*].titulo", contains("Apartamento")));
		buscar(get(RUTA).param("ciudadId", "2").param("precioMin", "160000").param("precioMax", "450000"))
				.andExpect(jsonPath("$.alojamientos[*].titulo", contains("Básica", "Grande")));
		buscar(get(RUTA).param("ciudadId", "2").param("servicioIds", "1").param("servicioIds", "2"))
				.andExpect(jsonPath("$.alojamientos[*].titulo", contains("Grande")));
		// Combinados: deben cumplirse todos a la vez
		buscar(get(RUTA).param("ciudadId", "2").param("huespedes", "6").param("tipoId", "1"))
				.andExpect(jsonPath("$.alojamientos", empty()));
		buscar(get(RUTA).param("ciudadId", "2").param("huespedes", "4").param("tipoId", "3")
				.param("precioMax", "300000").param("servicioIds", "1"))
				.andExpect(jsonPath("$.alojamientos[*].titulo", contains("Básica")));
		buscar(get(RUTA).param("ciudadId", "2").param("huespedes", "6").param("tipoId", "3")
				.param("precioMin", "400000").param("servicioIds", "2"))
				.andExpect(jsonPath("$.alojamientos[*].titulo", contains("Grande")));
		buscar(get(RUTA).param("ciudadId", "2").param("tamano", "2").param("pagina", "1"))
				.andExpect(jsonPath("$.alojamientos[*].titulo", contains("Grande")))
				.andExpect(jsonPath("$.total").value(3))
				.andExpect(jsonPath("$.totalPaginas").value(2));
	}

	@Test
	void conFechasSoloAparecenLosDisponiblesParaTodoElRango() throws Exception {
		long conPeriodo = alojamiento("Con periodo");
		alojamiento("Sin periodo");
		disponible(conPeriodo);
		String llegada = HOY.plusDays(10).toString();
		String salida = HOY.plusDays(13).toString();

		buscar(get(RUTA).param("ciudadId", "2").param("llegada", llegada).param("salida", salida))
				.andExpect(jsonPath("$.alojamientos[*].titulo", contains("Con periodo")));
		// Fuera del periodo disponible (termina en 60 días)
		buscar(get(RUTA).param("ciudadId", "2").param("llegada", HOY.plusDays(58).toString())
				.param("salida", HOY.plusDays(62).toString()))
				.andExpect(jsonPath("$.alojamientos", empty()));

		api.como(huesped, post("/api/reservas").header("Idempotency-Key", "r-1").content("""
				{"alojamientoId":%d,"llegada":"%s","salida":"%s","huespedes":2}"""
				.formatted(conPeriodo, HOY.plusDays(11), HOY.plusDays(12)))).andExpect(status().isCreated());

		// Una reserva que se cruza lo saca; otras fechas siguen libres
		buscar(get(RUTA).param("ciudadId", "2").param("llegada", llegada).param("salida", salida))
				.andExpect(jsonPath("$.alojamientos", empty()))
				.andExpect(jsonPath("$.mensaje", containsString("No encontramos alojamientos")));
		buscar(get(RUTA).param("ciudadId", "2").param("llegada", HOY.plusDays(12).toString())
				.param("salida", HOY.plusDays(15).toString()))
				.andExpect(jsonPath("$.alojamientos[*].titulo", contains("Con periodo")));
	}

	@Test
	void losFiltrosInvalidosSeRechazan() throws Exception {
		buscar(get(RUTA).param("ciudadId", "2").param("precioMin", "300000").param("precioMax", "100000"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("RANGO_PRECIO_INVALIDO"));
		buscar(get(RUTA).param("ciudadId", "2").param("precioMin", "-1")).andExpect(status().isBadRequest());
		buscar(get(RUTA).param("ciudadId", "2").param("llegada", HOY.plusDays(3).toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("FECHAS_INVALIDAS"));
		buscar(get(RUTA).param("ciudadId", "2").param("llegada", HOY.minusDays(1).toString())
				.param("salida", HOY.plusDays(2).toString()))
				.andExpect(status().isBadRequest());
		buscar(get(RUTA).param("ciudadId", "2").param("huespedes", "0")).andExpect(status().isBadRequest());
		buscar(get(RUTA).param("ciudadId", "2").param("tamano", "51")).andExpect(status().isBadRequest());
	}
}
