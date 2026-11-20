package co.edu.unbosque.marketplace.reservas.presentacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import co.edu.unbosque.marketplace.reservas.aplicacion.ConciliadorReservas;
import co.edu.unbosque.marketplace.reservas.aplicacion.ReservaTransacciones;
import co.edu.unbosque.marketplace.reservas.presentacion.PasarelaDePrueba.Modo;

/**
 * Consultar, cancelar con devolución y completar reservas contra PostgreSQL real (MA-70 a MA-74).
 * Requiere Docker en ejecución.
 */
@Import({ TestcontainersConfiguration.class, ReservaIntegrationTest.Pasarela.class })
@SpringBootTest
@ActiveProfiles("test")
class GestionReservasIntegrationTest {

	private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));
	private static final LocalDate LLEGADA = HOY.plusDays(10);
	private static final LocalDate SALIDA = HOY.plusDays(13);

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
	private ReservaTransacciones transacciones;

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

	private long reservar(String token, String clave, LocalDate llegada, LocalDate salida) throws Exception {
		String respuesta = api.como(token, post("/api/reservas").header("Idempotency-Key", clave).content("""
				{"alojamientoId":%d,"llegada":"%s","salida":"%s","huespedes":2}"""
				.formatted(alojamientoId, llegada, salida)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ApiDePrueba.id(respuesta);
	}

	private ResultActions cancelar(String token, long reservaId, String cuerpo) throws Exception {
		return api.como(token, post("/api/reservas/" + reservaId + "/cancelacion").content(cuerpo));
	}

	private ResultActions disponibilidad() throws Exception {
		return api.mvc().perform(get("/api/alojamientos/" + alojamientoId + "/disponibilidad")
				.param("llegada", LLEGADA.toString()).param("salida", SALIDA.toString()));
	}

	private String estadoReserva(long id) {
		return jdbc.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, id);
	}

	private BigDecimal columna(String nombre, long reservaId) {
		return jdbc.queryForObject("SELECT " + nombre + " FROM reserva WHERE id = ?", BigDecimal.class, reservaId);
	}

	private String referencia(long reservaId) {
		return jdbc.queryForObject("SELECT referencia_pago FROM reserva WHERE id = ?", String.class, reservaId);
	}

	private String ultimoEvento(long reservaId) {
		return jdbc.queryForObject("SELECT tipo FROM evento_outbox WHERE agregado_id = ? ORDER BY id DESC LIMIT 1",
				String.class, reservaId);
	}

	// ─────────────────────────────────────────
	// MA-70: consultar
	// ─────────────────────────────────────────

	@Test
	void cadaUnoVeSoloLasReservasQueLeCorresponden() throws Exception {
		long primera = reservar(huesped, "r-1", LLEGADA, SALIDA);
		long segunda = reservar(huesped, "r-2", HOY.plusDays(20), HOY.plusDays(22));
		String otroHuesped = api.registrarEIniciarSesion("otro@correo.com", "HUESPED");
		long ajena = reservar(otroHuesped, "r-3", HOY.plusDays(30), HOY.plusDays(32));
		String otroAnfitrion = api.registrarEIniciarSesion("otro.anfitrion@correo.com", "ANFITRION");

		api.como(huesped, get("/api/reservas"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				// Las más recientes primero
				.andExpect(jsonPath("$[0].id").value(segunda))
				.andExpect(jsonPath("$[1].id").value(primera))
				.andExpect(jsonPath("$[0].estado").value("CONFIRMADA"));
		api.como(otroHuesped, get("/api/reservas")).andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(ajena));
		api.como(anfitrion, get("/api/reservas")).andExpect(jsonPath("$", hasSize(3)));
		api.como(otroAnfitrion, get("/api/reservas")).andExpect(jsonPath("$", hasSize(0)));
		api.como(administrador, get("/api/reservas")).andExpect(jsonPath("$", hasSize(3)));

		// MA-70, escenario 3: las reservas ajenas no existen para quien no las puede ver
		api.como(otroHuesped, get("/api/reservas/" + primera)).andExpect(status().isNotFound());
		api.como(otroAnfitrion, get("/api/reservas/" + primera)).andExpect(status().isNotFound());
		api.mvc().perform(get("/api/reservas")).andExpect(status().isUnauthorized());
	}

	@Test
	void sePuedeFiltrarPorEstado() throws Exception {
		long cancelada = reservar(huesped, "r-1", LLEGADA, SALIDA);
		reservar(huesped, "r-2", HOY.plusDays(20), HOY.plusDays(22));
		cancelar(huesped, cancelada, "{\"motivo\":\"Cambio de planes\"}").andExpect(status().isOk());

		api.como(huesped, get("/api/reservas").param("estado", "CANCELADA"))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(cancelada))
				.andExpect(jsonPath("$[0].motivoCancelacion").value("Cambio de planes"));
		api.como(huesped, get("/api/reservas").param("estado", "CONFIRMADA")).andExpect(jsonPath("$", hasSize(1)));
		api.como(huesped, get("/api/reservas").param("estado", "NO_EXISTE")).andExpect(status().isBadRequest());
	}

	// ─────────────────────────────────────────
	// MA-72: cancelar
	// ─────────────────────────────────────────

	@Test
	void elHuespedCancelaSuReservaYLasFechasQuedanLibres() throws Exception {
		long id = reservar(huesped, "r-1", LLEGADA, SALIDA);
		disponibilidad().andExpect(jsonPath("$.disponible").value(false));

		cancelar(huesped, id, "{\"motivo\":\"Cambio de planes\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CANCELADA"))
				.andExpect(jsonPath("$.motivoCancelacion").value("Cambio de planes"))
				.andExpect(jsonPath("$.canceladaEn").isNotEmpty())
				// MA-73: con 48 horas o más de anticipación se devuelve el total
				.andExpect(jsonPath("$.valorDevolucion").value(693000.0))
				.andExpect(jsonPath("$.devolucionPendiente").value(false));

		disponibilidad().andExpect(jsonPath("$.disponible").value(true));
		assertThat(jdbc.queryForObject("SELECT cancelada_por = huesped_id FROM reserva WHERE id = ?", Boolean.class,
				id)).isTrue();
		assertThat(pasarela.montoDevuelto(referencia(id))).isEqualByComparingTo("693000");
		assertThat(jdbc.queryForList("SELECT tipo FROM evento_outbox WHERE agregado_id = ? ORDER BY id", String.class,
				id)).containsExactly("RESERVA_CONFIRMADA", "RESERVA_CANCELADA", "DEVOLUCION_SOLICITADA");
		// Otro huésped puede reservar las mismas fechas
		String otro = api.registrarEIniciarSesion("otro@correo.com", "HUESPED");
		reservar(otro, "r-2", LLEGADA, SALIDA);

		// MA-72, escenario 3: una reserva ya cancelada no se vuelve a cancelar
		cancelar(huesped, id, "{\"motivo\":\"Otra vez\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CANCELACION_NO_PERMITIDA"));
	}

	@Test
	void sinMotivoOSinSerElHuespedNoSeCancela() throws Exception {
		long id = reservar(huesped, "r-1", LLEGADA, SALIDA);
		String otro = api.registrarEIniciarSesion("otro@correo.com", "HUESPED");

		cancelar(huesped, id, "{\"motivo\":\"  \"}").andExpect(status().isBadRequest());
		cancelar(huesped, id, "{}").andExpect(status().isBadRequest());
		cancelar(huesped, id, "{\"motivo\":\"%s\"}".formatted("x".repeat(501))).andExpect(status().isBadRequest());
		// MA-74, escenario 2: el anfitrión no puede cancelar reservas
		cancelar(anfitrion, id, "{\"motivo\":\"No quiero\"}").andExpect(status().isForbidden());
		cancelar(otro, id, "{\"motivo\":\"No es mía\"}").andExpect(status().isNotFound());
		cancelar(huesped, 999_999, "{\"motivo\":\"No existe\"}").andExpect(status().isNotFound());

		assertThat(estadoReserva(id)).isEqualTo("CONFIRMADA");
		disponibilidad().andExpect(jsonPath("$.disponible").value(false));
	}

	@Test
	void unaReservaEnCursoNoSePuedeCancelar() throws Exception {
		long id = reservar(huesped, "r-1", LLEGADA, SALIDA);
		jdbc.update("UPDATE reserva SET fecha_llegada = ? WHERE id = ?", HOY, id);

		cancelar(huesped, id, "{\"motivo\":\"Me fui antes\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CANCELACION_NO_PERMITIDA"));
		assertThat(estadoReserva(id)).isEqualTo("CONFIRMADA");
	}

	// ─────────────────────────────────────────
	// MA-73, MA-74: devolución
	// ─────────────────────────────────────────

	@Test
	void conMenosDeCuarentaYOchoHorasSeDevuelveLaMitad() throws Exception {
		long id = reservar(huesped, "r-1", HOY.plusDays(1), HOY.plusDays(3));

		cancelar(huesped, id, "{\"motivo\":\"Último momento\"}").andExpect(status().isOk());

		BigDecimal total = columna("total", id);
		assertThat(columna("valor_devolucion", id)).isEqualByComparingTo(total.divide(BigDecimal.TWO));
		assertThat(pasarela.montoDevuelto(referencia(id))).isEqualByComparingTo(total.divide(BigDecimal.TWO));
	}

	@Test
	void mientrasUnaEjecucionPideLaDevolucionOtraNoEntraALaPasarela() throws Exception {
		long id = reservar(huesped, "r-1", LLEGADA, SALIDA);
		pasarela.modo(Modo.CAIDA);
		cancelar(huesped, id, "{\"motivo\":\"Cambio de planes\"}").andExpect(status().isOk());
		jdbc.update("UPDATE reserva SET devolucion_reclamada_hasta = NULL WHERE id = ?", id);
		pasarela.modo(Modo.APROBAR);
		CountDownLatch dentro = new CountDownLatch(1);
		CountDownLatch soltar = new CountDownLatch(1);
		pasarela.detenerSiguienteDevolucion(dentro, soltar);

		ExecutorService hilo = Executors.newSingleThreadExecutor();
		try {
			Future<Integer> primera = hilo.submit(() -> conciliador.reintentarDevoluciones());
			assertThat(dentro.await(30, TimeUnit.SECONDS)).isTrue();

			// La primera sigue dentro de la pasarela: la segunda no entra
			assertThat(conciliador.reintentarDevoluciones()).isZero();

			soltar.countDown();
			assertThat(primera.get(30, TimeUnit.SECONDS)).isEqualTo(1);
		} finally {
			hilo.shutdownNow();
		}
		String referencia = referencia(id);
		// El intento fallido (sin respuesta) y el reintento usan la misma clave
		assertThat(pasarela.llamadasDevolucion()).containsExactly(
				referencia + " 693000 reserva-" + id + "-cancelacion 1",
				referencia + " 693000 reserva-" + id + "-cancelacion 1");
		assertThat(conciliador.reintentarDevoluciones()).isZero();
	}

	@Test
	void siCancelaElAdministradorSeDevuelveElTotal() throws Exception {
		long id = reservar(huesped, "r-1", HOY.plusDays(1), HOY.plusDays(3));

		cancelar(administrador, id, "{\"motivo\":\"Alojamiento no disponible\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CANCELADA"));

		assertThat(columna("valor_devolucion", id)).isEqualByComparingTo(columna("total", id));
		assertThat(jdbc.queryForObject("SELECT u.rol FROM reserva r JOIN usuario u ON u.id = r.cancelada_por "
				+ "WHERE r.id = ?", String.class, id)).isEqualTo("ADMINISTRADOR");
	}

	@Test
	void siLaPasarelaFallaLaCancelacionQuedaCompletaYElConciliadorDevuelveDespues() throws Exception {
		long id = reservar(huesped, "r-1", LLEGADA, SALIDA);
		pasarela.modo(Modo.CAIDA);

		cancelar(huesped, id, "{\"motivo\":\"Cambio de planes\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CANCELADA"))
				.andExpect(jsonPath("$.devolucionPendiente").value(true));

		// MA-74, escenario 3: nada a medias; la reserva quedó cancelada y las fechas libres
		assertThat(estadoReserva(id)).isEqualTo("CANCELADA");
		disponibilidad().andExpect(jsonPath("$.disponible").value(true));
		assertThat(pasarela.devoluciones()).isZero();
		assertThat(conciliador.reintentarDevoluciones()).isZero();

		pasarela.modo(Modo.APROBAR);
		// El intento fallido sigue reclamado unos minutos por si la petición seguía en la pasarela
		assertThat(conciliador.reintentarDevoluciones()).isZero();
		jdbc.update("UPDATE reserva SET devolucion_reclamada_hasta = now() - interval '1 second' WHERE id = ?", id);
		assertThat(conciliador.reintentarDevoluciones()).isEqualTo(1);

		assertThat(pasarela.montoDevuelto(referencia(id))).isEqualByComparingTo("693000");
		assertThat(jdbc.queryForObject("SELECT devolucion_pendiente FROM reserva WHERE id = ?", Boolean.class, id))
				.isFalse();
		assertThat(conciliador.reintentarDevoluciones()).isZero();
		assertThat(pasarela.devoluciones()).isEqualTo(1);
		assertThat(pasarela.llamadasDevolucion()).hasSize(2);
	}

	// ─────────────────────────────────────────
	// MA-71: completar
	// ─────────────────────────────────────────

	@Test
	void alLlegarLaFechaDeSalidaLaReservaQuedaCompletada() throws Exception {
		long terminada = reservar(huesped, "r-1", LLEGADA, SALIDA);
		long futura = reservar(huesped, "r-2", HOY.plusDays(20), HOY.plusDays(22));
		jdbc.update("UPDATE reserva SET fecha_llegada = ?, fecha_salida = ? WHERE id = ?", HOY.minusDays(3), HOY,
				terminada);

		assertThat(conciliador.completarTerminadas()).isEqualTo(1);

		assertThat(estadoReserva(terminada)).isEqualTo("COMPLETADA");
		assertThat(estadoReserva(futura)).isEqualTo("CONFIRMADA");
		assertThat(ultimoEvento(terminada)).isEqualTo("RESERVA_COMPLETADA");
		assertThat(conciliador.completarTerminadas()).isZero();
		cancelar(huesped, terminada, "{\"motivo\":\"Tarde\"}").andExpect(status().isConflict());

		// Un reintento tardío con el mismo cobro no lo devuelve: ese cobro pagó la estadía
		String referencia = jdbc.queryForObject("SELECT referencia_pago FROM reserva WHERE id = ?", String.class,
				terminada);
		assertThat(transacciones.confirmar(terminada, referencia).requiereDevolucion()).isFalse();
		assertThat(estadoReserva(terminada)).isEqualTo("COMPLETADA");
	}
}
