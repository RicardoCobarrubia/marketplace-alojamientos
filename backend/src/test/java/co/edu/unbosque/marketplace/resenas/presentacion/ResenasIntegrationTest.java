package co.edu.unbosque.marketplace.resenas.presentacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import co.edu.unbosque.marketplace.notificaciones.aplicacion.ProcesadorOutbox;
import co.edu.unbosque.marketplace.reservas.aplicacion.ConciliadorReservas;
import co.edu.unbosque.marketplace.reservas.presentacion.PasarelaDePrueba;
import co.edu.unbosque.marketplace.reservas.presentacion.PasarelaDePruebaConfig;

/** Reseñas contra PostgreSQL real (MA-77 a MA-79). Requiere Docker en ejecución. */
@Import({ TestcontainersConfiguration.class, PasarelaDePruebaConfig.class })
@SpringBootTest
@ActiveProfiles("test")
class ResenasIntegrationTest {

	private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));
	private static final String RESENA = "{\"calificacion\":%d,\"comentario\":\"%s\"}";

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
	private ConciliadorReservas conciliador;
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

	private long reservar(String token, String clave, int desde) throws Exception {
		return ApiDePrueba.id(api.como(token, post("/api/reservas").header("Idempotency-Key", clave).content("""
				{"alojamientoId":%d,"llegada":"%s","salida":"%s","huespedes":2}"""
				.formatted(alojamientoId, HOY.plusDays(desde), HOY.plusDays(desde + 3))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	/** Una estadía que terminó hace {@code dias} días y que el conciliador ya completó. */
	private long estadiaCompletada(String token, String clave, int desde, int dias) throws Exception {
		long reserva = reservar(token, clave, desde);
		jdbc.update("UPDATE reserva SET fecha_llegada = ?, fecha_salida = ? WHERE id = ?", HOY.minusDays(dias + 3L),
				HOY.minusDays(dias), reserva);
		conciliador.completarTerminadas();
		return reserva;
	}

	private ResultActions resenar(String token, long reserva, int calificacion, String comentario) throws Exception {
		return api.como(token, post("/api/reservas/" + reserva + "/resena")
				.content(RESENA.formatted(calificacion, comentario)));
	}

	private ResultActions detalle() throws Exception {
		return api.mvc().perform(get("/api/alojamientos/" + alojamientoId + "/resenas"));
	}

	// ─────────────────────────────────────────
	// MA-77, MA-78: publicar
	// ─────────────────────────────────────────

	@Test
	void elHuespedResenaSuEstadiaYApareceEnElDetalleConSuPromedio() throws Exception {
		long primera = estadiaCompletada(huesped, "r-1", 10, 2);
		String otro = api.registrarEIniciarSesion("otro@correo.com", "HUESPED");
		long segunda = estadiaCompletada(otro, "r-2", 20, 5);
		detalle().andExpect(status().isOk()).andExpect(jsonPath("$.cantidad").value(0))
				.andExpect(jsonPath("$.promedio").doesNotExist());

		resenar(huesped, primera, 5, "Excelente estadía, muy limpio y cómodo.")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.reservaId").value(primera))
				.andExpect(jsonPath("$.alojamientoId").value(alojamientoId))
				.andExpect(jsonPath("$.oculta").value(false));
		resenar(otro, segunda, 4, "Buena ubicación, la cama un poco dura.").andExpect(status().isCreated());

		// El detalle es público: no necesita sesión
		detalle()
				.andExpect(jsonPath("$.cantidad").value(2))
				.andExpect(jsonPath("$.promedio").value(4.5))
				.andExpect(jsonPath("$.resenas[0].calificacion").value(4))
				.andExpect(jsonPath("$.resenas[0].huespedId").doesNotExist());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE accion = 'PUBLICAR_RESENA'"
				+ " AND entidad_id IN (SELECT id FROM resena WHERE reserva_id IN (?, ?))", Integer.class, primera,
				segunda)).isEqualTo(2);
	}

	@Test
	void soloUnaResenaPorReservaYNoSePuedeEditar() throws Exception {
		long reserva = estadiaCompletada(huesped, "r-1", 10, 2);
		resenar(huesped, reserva, 5, "Excelente estadía, muy limpio.").andExpect(status().isCreated());

		resenar(huesped, reserva, 1, "Cambié de opinión sobre todo.")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("RESENA_DUPLICADA"));
		// MA-77, escenario 3: no hay forma de editarla
		api.como(huesped, put("/api/reservas/" + reserva + "/resena").content(RESENA.formatted(1, "Nuevo texto largo")))
				.andExpect(status().isMethodNotAllowed());
		detalle().andExpect(jsonPath("$.promedio").value(5.0));
	}

	@Test
	void seRechazaSiNoEstaCompletadaEsAjenaOFueraDePlazo() throws Exception {
		long confirmada = reservar(huesped, "r-1", 10);
		long vencida = estadiaCompletada(huesped, "r-2", 20, 31);
		long completada = estadiaCompletada(huesped, "r-3", 30, 2);
		String otro = api.registrarEIniciarSesion("otro@correo.com", "HUESPED");

		resenar(huesped, confirmada, 5, "Todavía no he llegado.").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("RESENA_NO_PERMITIDA"));
		resenar(huesped, vencida, 5, "Me acordé muy tarde de reseñar.").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("PLAZO_VENCIDO"));
		resenar(otro, completada, 1, "No me hospedé pero opino.").andExpect(status().isNotFound());
		resenar(anfitrion, completada, 5, "Mi propio alojamiento es genial.").andExpect(status().isForbidden());
		resenar(huesped, completada, 6, "Calificación fuera de rango.").andExpect(status().isBadRequest());
		resenar(huesped, completada, 5, "Corto").andExpect(status().isBadRequest());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM resena", Integer.class)).isZero();
	}

	// ─────────────────────────────────────────
	// MA-79: moderar
	// ─────────────────────────────────────────

	@Test
	void elAdministradorOcultaYMuestraUnaResenaYSeNotificaAlHuesped() throws Exception {
		long reserva = estadiaCompletada(huesped, "r-1", 10, 2);
		long resena = ApiDePrueba.id(resenar(huesped, reserva, 1, "Contiene publicidad de otro sitio web.")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		api.como(huesped, post("/api/admin/resenas/" + resena + "/ocultamiento").content("{\"motivo\":\"Publicidad\"}"))
				.andExpect(status().isForbidden());
		api.como(administrador, post("/api/admin/resenas/" + resena + "/ocultamiento").content("{}"))
				.andExpect(status().isBadRequest());
		api.como(administrador, post("/api/admin/resenas/" + resena + "/ocultamiento")
				.content("{\"motivo\":\"Publicidad\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.oculta").value(true))
				.andExpect(jsonPath("$.motivoOcultamiento").value("Publicidad"));

		// Oculta: no se muestra ni cuenta en el promedio
		detalle().andExpect(jsonPath("$.cantidad").value(0)).andExpect(jsonPath("$.promedio").doesNotExist());
		api.como(administrador, get("/api/admin/resenas").param("oculta", "true")).andExpect(jsonPath("$", hasSize(1)));
		procesador.procesarPendientes();
		api.como(huesped, get("/api/notificaciones"))
				.andExpect(jsonPath("$[0].tipo").value("RESENA_OCULTA"))
				.andExpect(jsonPath("$[0].mensaje").value(containsString("Publicidad")));

		api.como(administrador, delete("/api/admin/resenas/" + resena + "/ocultamiento"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.oculta").value(false));
		detalle().andExpect(jsonPath("$.cantidad").value(1)).andExpect(jsonPath("$.promedio").value(1.0));
		assertThat(jdbc.queryForList("SELECT accion FROM auditoria WHERE entidad = 'RESENA' AND entidad_id = ?"
				+ " ORDER BY id", String.class, resena))
				.containsExactly("PUBLICAR_RESENA", "OCULTAR_RESENA", "MOSTRAR_RESENA");
	}

	@Test
	void lasResenasDeUnAlojamientoNoPublicadoNoSeMuestran() throws Exception {
		api.como(anfitrion, post("/api/alojamientos/" + alojamientoId + "/pausar")).andExpect(status().isOk());

		detalle().andExpect(status().isNotFound());
	}
}
