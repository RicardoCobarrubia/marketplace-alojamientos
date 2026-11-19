package co.edu.unbosque.marketplace.pagos.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import co.edu.unbosque.marketplace.pagos.dominio.PasarelaNoDisponibleException;
import co.edu.unbosque.marketplace.pagos.dominio.ResultadoPago;
import co.edu.unbosque.marketplace.pagos.dominio.ResultadoPago.EstadoPago;
import co.edu.unbosque.marketplace.pagos.dominio.SolicitudCobro;
import tools.jackson.databind.json.JsonMapper;

/** StripeAdapter contra un servidor HTTP local que imita la API de Stripe. */
class StripeAdapterTest {

	private static final BigDecimal MONTO = new BigDecimal("150000.50");
	private static final SolicitudCobro SOLICITUD = new SolicitudCobro("reserva-7", MONTO, "Reserva 7");

	private HttpServer servidor;
	private final List<String> peticiones = new CopyOnWriteArrayList<>();
	private volatile int codigoConfirmacion = 200;
	private volatile String errorConfirmacion = "{\"error\":{\"type\":\"card_error\",\"message\":\"Your card was declined.\"}}";
	private volatile String estadoConsultado = "succeeded";
	private volatile String devolucionesExistentes = "[]";
	private volatile int codigoDevolucion = 200;
	private volatile long demoraMs;
	private StripeAdapter adaptador;

	@BeforeEach
	void iniciar() throws IOException {
		servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		servidor.createContext("/v1/", this::atender);
		servidor.start();
		adaptador = adaptador(Duration.ofMillis(500));
	}

	@AfterEach
	void detener() {
		servidor.stop(0);
	}

	private StripeAdapter adaptador(Duration timeout) {
		return new StripeAdapter(JsonMapper.builder().build(), "http://127.0.0.1:" + servidor.getAddress().getPort(),
				"sk_test_prueba", "cop", "pm_card_visa", timeout);
	}

	/** Imita las rutas de Stripe que usa el adaptador y registra cada petición. */
	private void atender(HttpExchange intercambio) throws IOException {
		String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		String metodo = intercambio.getRequestMethod();
		String ruta = intercambio.getRequestURI().getPath();
		peticiones.add(metodo + " " + intercambio.getRequestURI() + " "
				+ intercambio.getRequestHeaders().getFirst("Idempotency-Key") + " "
				+ intercambio.getRequestHeaders().getFirst("Authorization") + " " + cuerpo);
		esperar(demoraMs);
		if ("POST".equals(metodo) && ruta.equals("/v1/payment_intents")) {
			responder(intercambio, 200, "{\"id\":\"pi_1\",\"status\":\"requires_confirmation\"}");
		} else if ("POST".equals(metodo) && ruta.equals("/v1/payment_intents/pi_1/confirm")) {
			responder(intercambio, codigoConfirmacion,
					codigoConfirmacion >= 400 ? errorConfirmacion : "{\"id\":\"pi_1\",\"status\":\"succeeded\"}");
		} else if ("GET".equals(metodo) && ruta.equals("/v1/payment_intents/pi_1")) {
			responder(intercambio, 200, "{\"id\":\"pi_1\",\"status\":\"" + estadoConsultado + "\"}");
		} else if ("GET".equals(metodo) && ruta.equals("/v1/refunds")) {
			responder(intercambio, 200, "{\"data\":" + devolucionesExistentes + "}");
		} else if ("POST".equals(metodo) && ruta.equals("/v1/refunds")) {
			responder(intercambio, codigoDevolucion, codigoDevolucion >= 400
					? "{\"error\":{\"type\":\"api_error\",\"message\":\"Error interno\"}}"
					: "{\"id\":\"re_1\",\"status\":\"succeeded\"}");
		} else {
			responder(intercambio, 404, "{\"error\":{\"message\":\"No existe " + ruta + "\"}}");
		}
	}

	/** Simula la latencia de Stripe sin Thread.sleep: espera un evento que nunca llega. */
	private static void esperar(long milisegundos) {
		try {
			new CountDownLatch(1).await(milisegundos, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static void responder(HttpExchange intercambio, int codigo, String cuerpo) throws IOException {
		byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
		intercambio.getResponseHeaders().add("Content-Type", "application/json");
		intercambio.sendResponseHeaders(codigo, bytes.length);
		try (OutputStream salida = intercambio.getResponseBody()) {
			salida.write(bytes);
		}
	}

	// ─────────────────────────────────────────
	// Preparar y cobrar
	// ─────────────────────────────────────────

	@Test
	void prepararCreaElPaymentIntentSinCobrarYDevuelveSuId() {
		assertThat(adaptador.preparar(SOLICITUD)).isEqualTo("pi_1");

		assertThat(peticiones).singleElement().asString()
				.startsWith("POST /v1/payment_intents reserva-7:crear Bearer sk_test_prueba")
				.contains("amount=15000050", "currency=cop", "metadata%5Bclave_idempotencia%5D=reserva-7")
				.doesNotContain("confirm");
	}

	@Test
	void cobrarConfirmaElPaymentIntentPreparado() {
		ResultadoPago resultado = adaptador.cobrar("pi_1", SOLICITUD);

		assertThat(resultado).isEqualTo(ResultadoPago.aprobado("pi_1"));
		assertThat(peticiones).singleElement().asString()
				.startsWith("POST /v1/payment_intents/pi_1/confirm reserva-7:confirmar");
	}

	@Test
	void unaTarjetaRechazadaEsUnPagoRechazado() {
		codigoConfirmacion = 402;

		ResultadoPago resultado = adaptador.cobrar("pi_1", SOLICITUD);

		assertThat(resultado.estado()).isEqualTo(EstadoPago.RECHAZADO);
		assertThat(resultado.detalle()).isEqualTo("Your card was declined.");
	}

	@Test
	void unErrorDeLaPeticionNoEsUnRechazoDeLaTarjeta() {
		codigoConfirmacion = 400;
		errorConfirmacion = "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"Parámetro inválido\"}}";

		assertThatThrownBy(() -> adaptador.cobrar("pi_1", SOLICITUD))
				.isInstanceOf(PasarelaNoDisponibleException.class);
	}

	@Test
	void unErrorDelServidorDejaElCobroEnDuda() {
		codigoConfirmacion = 500;
		errorConfirmacion = "{\"error\":{\"type\":\"api_error\",\"message\":\"Error\"}}";

		assertThatThrownBy(() -> adaptador.cobrar("pi_1", SOLICITUD))
				.isInstanceOf(PasarelaNoDisponibleException.class);
	}

	@Test
	void siStripeNoRespondeATiempoElCobroQuedaEnDuda() {
		demoraMs = 1500;

		assertThatThrownBy(() -> adaptador.cobrar("pi_1", SOLICITUD))
				.isInstanceOf(PasarelaNoDisponibleException.class);
	}

	@Test
	void conPlazoSuficienteLaMismaDemoraSiAlcanza() {
		demoraMs = 300;

		assertThat(adaptador(Duration.ofSeconds(3)).cobrar("pi_1", SOLICITUD).aprobado()).isTrue();
	}

	@Test
	void unCuerpoQueNoTerminaDeLlegarTambienAgotaElPlazo() {
		servidor.removeContext("/v1/");
		servidor.createContext("/v1/", intercambio -> {
			intercambio.getRequestBody().readAllBytes();
			intercambio.sendResponseHeaders(200, 0);
			OutputStream salida = intercambio.getResponseBody();
			salida.write("{\"id\":".getBytes(StandardCharsets.UTF_8));
			salida.flush();
			esperar(1500);
			salida.write("\"pi_1\"}".getBytes(StandardCharsets.UTF_8));
			salida.close();
		});

		assertThatThrownBy(() -> adaptador.preparar(SOLICITUD)).isInstanceOf(PasarelaNoDisponibleException.class);
	}

	// ─────────────────────────────────────────
	// Consultar
	// ─────────────────────────────────────────

	@Test
	void consultarLeeElPaymentIntentPorSuIdSinCrearOtro() {
		assertThat(adaptador.consultar("pi_1")).isEqualTo(ResultadoPago.aprobado("pi_1"));
		estadoConsultado = "requires_confirmation";
		assertThat(adaptador.consultar("pi_1").estado()).isEqualTo(EstadoPago.NO_COBRADO);

		assertThat(peticiones).hasSize(2).allMatch(peticion -> peticion.startsWith("GET /v1/payment_intents/pi_1 "));
	}

	@Test
	void consultarUnaReferenciaInexistenteEsUnError() {
		assertThatThrownBy(() -> adaptador.consultar("pi_otro")).isInstanceOf(PasarelaNoDisponibleException.class);
	}

	// ─────────────────────────────────────────
	// Devolver
	// ─────────────────────────────────────────

	@Test
	void devolverRevisaLasDevolucionesYUsaUnaClavePorIntento() {
		adaptador.devolver("pi_1", MONTO, "reserva-7", 2);

		assertThat(peticiones).hasSize(2);
		assertThat(peticiones.get(0)).startsWith("GET /v1/refunds?limit=10&payment_intent=pi_1 ");
		assertThat(peticiones.get(1)).startsWith("POST /v1/refunds reserva-7:devolucion:2 ")
				.contains("payment_intent=pi_1", "amount=15000050");
	}

	@Test
	void siYaHayUnaDevolucionNoPideOtra() {
		devolucionesExistentes = "[{\"id\":\"re_1\",\"status\":\"pending\"}]";

		adaptador.devolver("pi_1", MONTO, "reserva-7", 3);

		assertThat(peticiones).singleElement().asString().startsWith("GET /v1/refunds");
	}

	@Test
	void unaDevolucionFallidaAnteriorNoImpideUnNuevoIntento() {
		devolucionesExistentes = "[{\"id\":\"re_1\",\"status\":\"failed\"}]";

		adaptador.devolver("pi_1", MONTO, "reserva-7", 2);

		assertThat(peticiones).anyMatch(peticion -> peticion.startsWith("POST /v1/refunds reserva-7:devolucion:2"));
	}

	@Test
	void unErrorAlDevolverQuedaParaReintentar() {
		codigoDevolucion = 500;

		assertThatThrownBy(() -> adaptador.devolver("pi_1", MONTO, "reserva-7", 1))
				.isInstanceOf(PasarelaNoDisponibleException.class);
	}

	// ─────────────────────────────────────────
	// Configuración
	// ─────────────────────────────────────────

	@Test
	void soloAdmiteClavesDePruebaMonedasConCentavosYHttps() {
		JsonMapper json = JsonMapper.builder().build();
		Duration timeout = Duration.ofSeconds(1);

		assertThatThrownBy(() -> new StripeAdapter(json, "https://api.stripe.com", " ", "cop", "pm_card_visa",
				timeout)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new StripeAdapter(json, "https://api.stripe.com", "sk_live_123", "cop",
				"pm_card_visa", timeout)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new StripeAdapter(json, "https://api.stripe.com", "sk_test_123", "jpy",
				"pm_card_visa", timeout)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new StripeAdapter(json, "http://api.stripe.com", "sk_test_123", "cop",
				"pm_card_visa", timeout)).isInstanceOf(IllegalStateException.class);
	}
}
