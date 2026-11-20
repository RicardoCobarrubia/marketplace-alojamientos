package co.edu.unbosque.marketplace.reservas.presentacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import co.edu.unbosque.marketplace.ApiDePrueba;
import co.edu.unbosque.marketplace.TestcontainersConfiguration;
import co.edu.unbosque.marketplace.pagos.dominio.PasarelaPagos;
import co.edu.unbosque.marketplace.reservas.aplicacion.ConciliadorReservas;
import co.edu.unbosque.marketplace.reservas.aplicacion.ReservaDTO;
import co.edu.unbosque.marketplace.reservas.aplicacion.ReservaTransacciones;
import co.edu.unbosque.marketplace.reservas.presentacion.PasarelaDePrueba.Modo;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.infraestructura.UsuarioRepository;
import co.edu.unbosque.marketplace.usuarios.infraestructura.seguridad.JwtUtil;

/**
 * Flujo de reserva contra PostgreSQL real (MA-66 a MA-69, MA-60), con una pasarela que permite
 * inyectar fallos. Requiere Docker en ejecución.
 */
@Import({ TestcontainersConfiguration.class, ReservaIntegrationTest.Pasarela.class })
@SpringBootTest
@ActiveProfiles("test")
class ReservaIntegrationTest {

	private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));
	private static final LocalDate LLEGADA = HOY.plusDays(10);
	private static final LocalDate SALIDA = HOY.plusDays(13);
	private static final Pattern RESERVA_ID = Pattern.compile("\"reservaId\":(\\d+)");

	@TestConfiguration
	static class Pasarela {
		@Bean
		@Primary
		PasarelaDePrueba pasarelaDePrueba() {
			return new PasarelaDePrueba();
		}
	}

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
	private PasarelaPagos pasarelaInyectada;
	@Autowired
	private ConciliadorReservas conciliador;
	@Autowired
	private ReservaTransacciones transacciones;
	@Autowired
	private UsuarioRepository usuarioRepository;
	@Autowired
	private JwtUtil jwtUtil;

	private ApiDePrueba api;
	private String anfitrion;
	private String huesped;
	private long alojamientoId;

	@BeforeEach
	void preparar() throws Exception {
		jdbc.execute("TRUNCATE TABLE evento_outbox RESTART IDENTITY");
		api = new ApiDePrueba(contexto, jdbc, passwordEncoder, cacheManager);
		pasarela.reiniciar();
		anfitrion = api.registrarEIniciarSesion("anfitrion@correo.com", "ANFITRION");
		huesped = api.registrarEIniciarSesion("huesped@correo.com", "HUESPED");
		alojamientoId = api.crearAlojamientoActivo(anfitrion, api.administrador());
		api.como(anfitrion, post("/api/alojamientos/" + alojamientoId + "/periodos")
				.content("{\"fechaInicio\":\"%s\",\"fechaFin\":\"%s\"}".formatted(HOY, HOY.plusDays(60))))
				.andExpect(status().isCreated());
	}

	// ─────────────────────────────────────────
	// Ayudas
	// ─────────────────────────────────────────

	private ResultActions reservar(String token, String clave, LocalDate llegada, LocalDate salida, int huespedes)
			throws Exception {
		var peticion = post("/api/reservas").content("""
				{"alojamientoId":%d,"llegada":"%s","salida":"%s","huespedes":%d}"""
				.formatted(alojamientoId, llegada, salida, huespedes));
		if (clave != null) {
			peticion.header("Idempotency-Key", clave);
		}
		return api.como(token, peticion);
	}

	private ResultActions disponibilidad() throws Exception {
		return api.mvc().perform(get("/api/alojamientos/" + alojamientoId + "/disponibilidad")
				.param("llegada", LLEGADA.toString()).param("salida", SALIDA.toString()));
	}

	private String estadoReserva(long id) {
		return jdbc.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, id);
	}

	private int contar(String sql, Object... parametros) {
		Integer cantidad = jdbc.queryForObject(sql, Integer.class, parametros);
		return cantidad == null ? 0 : cantidad;
	}

	/** Huéspedes creados directamente en la base de datos, con su token, para las pruebas de carga. */
	private List<String> huespedes(int cantidad) {
		String hash = passwordEncoder.encode(ApiDePrueba.CLAVE);
		List<String> tokens = new ArrayList<>();
		for (int i = 0; i < cantidad; i++) {
			String email = "carga" + i + "@correo.com";
			jdbc.update("INSERT INTO usuario (nombre, email, telefono, password_hash, rol) VALUES (?, ?, ?, ?, ?)",
					"Carga " + i, email, "3000000000", hash, "HUESPED");
			Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
			tokens.add(jwtUtil.generarToken(usuario));
		}
		return tokens;
	}

	private static List<MockHttpServletResponse> enParalelo(List<Callable<MockHttpServletResponse>> tareas)
			throws Exception {
		ExecutorService hilos = Executors.newFixedThreadPool(tareas.size());
		CountDownLatch salida = new CountDownLatch(1);
		try {
			List<Future<MockHttpServletResponse>> futuros = new ArrayList<>();
			for (Callable<MockHttpServletResponse> tarea : tareas) {
				futuros.add(hilos.submit(() -> {
					salida.await();
					return tarea.call();
				}));
			}
			salida.countDown();
			List<MockHttpServletResponse> respuestas = new ArrayList<>();
			for (Future<MockHttpServletResponse> futuro : futuros) {
				respuestas.add(futuro.get());
			}
			return respuestas;
		} finally {
			hilos.shutdownNow();
		}
	}

	// ─────────────────────────────────────────
	// MA-66, MA-67: solicitar y confirmar
	// ─────────────────────────────────────────

	@Test
	void usaLaPasarelaDePrueba() {
		assertThat(pasarelaInyectada).isSameAs(pasarela);
	}

	@Test
	void conPagoAprobadoQuedaConfirmadaYBloqueaLasFechas() throws Exception {
		String respuesta = reservar(huesped, "intento-1", LLEGADA, SALIDA, 2)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("CONFIRMADA"))
				.andExpect(jsonPath("$.noches").value(3))
				// 3 × 200.000 + 30.000 de limpieza, más el 10 % de tarifa de servicio
				.andExpect(jsonPath("$.total").value(693000.0))
				.andReturn().getResponse().getContentAsString();
		long id = ApiDePrueba.id(respuesta);

		disponibilidad().andExpect(jsonPath("$.disponible").value(false));
		assertThat(contar("SELECT count(*) FROM periodo_reservado WHERE reserva_id = ? AND retenido_hasta IS NULL "
				+ "AND liberado_en IS NULL", id)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT tipo FROM evento_outbox WHERE agregado_id = ?", String.class, id))
				.isEqualTo("RESERVA_CONFIRMADA");
		// ADR-012: la reserva y su evento quedan con el mismo id de correlación de la petición
		String correlacion = jdbc.queryForObject("SELECT correlation_id FROM reserva WHERE id = ?", String.class, id);
		assertThat(correlacion).isNotBlank();
		assertThat(jdbc.queryForObject("SELECT correlation_id FROM evento_outbox WHERE agregado_id = ?", String.class,
				id)).isEqualTo(correlacion);

		api.como(huesped, get("/api/reservas/" + id)).andExpect(status().isOk());
		api.como(anfitrion, get("/api/reservas/" + id)).andExpect(jsonPath("$.estado").value("CONFIRMADA"));
		String otro = api.registrarEIniciarSesion("otro@correo.com", "HUESPED");
		api.como(otro, get("/api/reservas/" + id)).andExpect(status().isNotFound());
	}

	@Test
	void rechazaSolicitudesInvalidas() throws Exception {
		reservar(anfitrion, "a-1", LLEGADA, SALIDA, 2).andExpect(status().isForbidden());
		reservar(huesped, null, LLEGADA, SALIDA, 2)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CLAVE_IDEMPOTENCIA_REQUERIDA"));
		reservar(huesped, "a-2", LLEGADA, SALIDA, 5)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("HUESPEDES_INVALIDOS"));
		reservar(huesped, "a-3", LLEGADA, SALIDA, 0)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.huespedes").exists());
		reservar(huesped, "a-4", HOY.plusDays(70), HOY.plusDays(72), 2)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("FUERA_DE_PERIODO"));

		api.como(anfitrion, post("/api/alojamientos/" + alojamientoId + "/pausar")).andExpect(status().isOk());
		reservar(huesped, "a-5", LLEGADA, SALIDA, 2)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("ALOJAMIENTO_NO_DISPONIBLE"));
		assertThat(contar("SELECT count(*) FROM reserva")).isZero();
		assertThat(pasarela.llamadasCobro()).isZero();
	}

	// ─────────────────────────────────────────
	// MA-68: fallos de la pasarela
	// ─────────────────────────────────────────

	@Test
	void conPagoRechazadoQuedaEnPagoFallidoYLiberaLasFechas() throws Exception {
		pasarela.modo(Modo.RECHAZAR);

		String respuesta = reservar(huesped, "intento-1", LLEGADA, SALIDA, 2)
				.andExpect(status().isPaymentRequired())
				.andExpect(jsonPath("$.codigo").value("PAGO_RECHAZADO"))
				.andReturn().getResponse().getContentAsString();
		Matcher reservaId = RESERVA_ID.matcher(respuesta);
		assertThat(reservaId.find()).isTrue();
		long id = Long.parseLong(reservaId.group(1));

		assertThat(estadoReserva(id)).isEqualTo("PAGO_FALLIDO");
		disponibilidad().andExpect(jsonPath("$.disponible").value(true));
		assertThat(jdbc.queryForObject("SELECT tipo FROM evento_outbox WHERE agregado_id = ?", String.class, id))
				.isEqualTo("PAGO_FALLIDO");
	}

	@Test
	void siLaPasarelaNoRespondePeroCobroSeDevuelveElPago() throws Exception {
		pasarela.modo(Modo.TIMEOUT_COBRADO);

		reservar(huesped, "intento-1", LLEGADA, SALIDA, 2)
				.andExpect(status().isGatewayTimeout())
				.andExpect(jsonPath("$.codigo").value("PASARELA_SIN_RESPUESTA"));

		assertThat(pasarela.devoluciones()).isEqualTo(1);
		assertThat(contar("SELECT count(*) FROM reserva WHERE estado = 'PAGO_FALLIDO'"
				+ " AND referencia_pago IS NOT NULL AND intentos_devolucion = 1")).isEqualTo(1);
		disponibilidad().andExpect(jsonPath("$.disponible").value(true));
	}

	@Test
	void siLaPasarelaNoRespondeAlPrepararNoHayCobroNiDudas() throws Exception {
		pasarela.modo(Modo.SIN_CONEXION);

		reservar(huesped, "intento-1", LLEGADA, SALIDA, 2)
				.andExpect(status().isGatewayTimeout())
				.andExpect(jsonPath("$.codigo").value("PASARELA_SIN_RESPUESTA"));

		assertThat(contar("SELECT count(*) FROM reserva WHERE estado = 'PAGO_FALLIDO'"
				+ " AND referencia_pago IS NULL AND NOT pago_por_verificar")).isEqualTo(1);
		assertThat(pasarela.llamadasCobro()).isZero();
		disponibilidad().andExpect(jsonPath("$.disponible").value(true));
	}

	@Test
	void siLaPasarelaEstaCaidaElConciliadorVerificaDespues() throws Exception {
		pasarela.modo(Modo.CAIDA);
		reservar(huesped, "intento-1", LLEGADA, SALIDA, 2).andExpect(status().isGatewayTimeout());
		assertThat(contar("SELECT count(*) FROM reserva WHERE pago_por_verificar AND referencia_pago IS NOT NULL"))
				.isEqualTo(1);

		pasarela.modo(Modo.APROBAR);
		assertThat(conciliador.verificarPagosEnDuda()).isEqualTo(1);

		assertThat(contar("SELECT count(*) FROM reserva WHERE pago_por_verificar")).isZero();
		assertThat(pasarela.devoluciones()).isZero();
	}

	// ─────────────────────────────────────────
	// MA-69: reintentos
	// ─────────────────────────────────────────

	@Test
	void unReintentoDevuelveLaMismaReservaSinCobrarOtraVez() throws Exception {
		String primera = reservar(huesped, "intento-1", LLEGADA, SALIDA, 2)
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

		String segunda = reservar(huesped, "intento-1", LLEGADA, SALIDA, 2)
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

		assertThat(segunda).isEqualTo(primera);
		assertThat(pasarela.cobros()).isEqualTo(1);
		reservar(huesped, "intento-1", LLEGADA, SALIDA, 3)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("IDEMPOTENCIA_CONFLICTO"));
	}

	@Test
	void cienReintentosSimultaneosCreanUnaReservaYUnCobro() throws Exception {
		List<Callable<MockHttpServletResponse>> tareas = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			tareas.add(() -> reservar(huesped, "intento-unico", LLEGADA, SALIDA, 2).andReturn().getResponse());
		}

		List<MockHttpServletResponse> respuestas = enParalelo(tareas);

		assertThat(respuestas).extracting(MockHttpServletResponse::getStatus).containsOnly(201, 202);
		assertThat(contar("SELECT count(*) FROM reserva")).isEqualTo(1);
		assertThat(contar("SELECT count(*) FROM reserva WHERE estado = 'CONFIRMADA'")).isEqualTo(1);
		assertThat(pasarela.cobros()).isEqualTo(1);
		assertThat(pasarela.llamadasCobro()).isEqualTo(1);
	}

	// ─────────────────────────────────────────
	// MA-60 y ADR-003: concurrencia y retenciones
	// ─────────────────────────────────────────

	@Test
	void deCincuentaSolicitudesSimultaneasConLasMismasFechasSeConfirmaUna() throws Exception {
		List<Callable<MockHttpServletResponse>> tareas = new ArrayList<>();
		List<Long> duraciones = Collections.synchronizedList(new ArrayList<>());
		int i = 0;
		for (String token : huespedes(50)) {
			String clave = "carga-" + i++;
			tareas.add(() -> {
				long inicio = System.nanoTime();
				MockHttpServletResponse respuesta = reservar(token, clave, LLEGADA, SALIDA, 2).andReturn().getResponse();
				duraciones.add((System.nanoTime() - inicio) / 1_000_000);
				return respuesta;
			});
		}

		List<MockHttpServletResponse> respuestas = enParalelo(tareas);

		assertThat(respuestas).filteredOn(respuesta -> respuesta.getStatus() == 201).hasSize(1);
		assertThat(respuestas).filteredOn(respuesta -> respuesta.getStatus() == 409).hasSize(49);
		assertThat(contar("SELECT count(*) FROM periodo_reservado WHERE liberado_en IS NULL")).isEqualTo(1);
		List<Long> ordenadas = duraciones.stream().sorted().toList();
		long p95 = ordenadas.get((int) Math.ceil(ordenadas.size() * 0.95) - 1);
		assertThat(p95).isLessThanOrEqualTo(5000);
	}

	@Test
	void unaRetencionVencidaSeLiberaYLasFechasQuedanParaOtro() throws Exception {
		Usuario primero = usuarioRepository.findByEmail("huesped@correo.com").orElseThrow();
		ReservaDTO solicitud = new ReservaDTO(null, alojamientoId, LLEGADA, SALIDA, 2, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null);
		// Una reserva que quedó en PENDIENTE_PAGO hace 11 minutos (el proceso cayó antes de pagar)
		long pendiente = transacciones.iniciar(primero, "abandonada", "huella", solicitud).getId();
		jdbc.update("UPDATE reserva SET creado_en = now() - interval '11 minutes' WHERE id = ?", pendiente);
		jdbc.update("UPDATE periodo_reservado SET retenido_hasta = now() - interval '1 minute' WHERE reserva_id = ?",
				pendiente);

		// Otro huésped puede tomar las fechas aunque el conciliador todavía no haya pasado
		String otro = api.registrarEIniciarSesion("otro@correo.com", "HUESPED");
		reservar(otro, "intento-2", LLEGADA, SALIDA, 2).andExpect(status().isCreated());

		assertThat(conciliador.vencerRetenciones()).isEqualTo(1);
		assertThat(estadoReserva(pendiente)).isEqualTo("PAGO_FALLIDO");
		assertThat(pasarela.devoluciones()).isZero();
	}
}
