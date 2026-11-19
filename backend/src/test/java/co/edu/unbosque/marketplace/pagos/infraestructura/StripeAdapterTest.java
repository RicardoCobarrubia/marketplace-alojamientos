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
import java.util.concurrent.atomic.AtomicInteger;

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

	private HttpServer servidor;
	private final List<String> peticiones = new CopyOnWriteArrayList<>();
	private final AtomicInteger confirmaciones = new AtomicInteger();
	private volatile int codigoConfirmacion = 200;
	private volatile String estadoConsultado = "succeeded";
	private volatile long demoraMs;
	private volatile String errorConfirmacion = "{\"error\":{\"type\":\"card_error\",\"message\":\"Your card was declined.\"}}";
	private StripeAdapter adaptador;

	@BeforeEach
	void iniciar() throws IOException {
		servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		servidor.createContext("/v1/payment_intents", this::intenciones);
		servidor.createContext("/v1/refunds", intercambio -> {
			peticiones.add(intercambio.getRequestMethod() + " " + intercambio.getRequestURI().getPath() + " "
					+ intercambio.getRequestHeaders().getFirst("Idempotency-Key") + " "
					+ new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			responder(intercambio, 200, "{\"id\":\"re_1\"}");
		});
		servidor.start();
		adaptador = new StripeAdapter(JsonMapper.builder().build(),
				"http://127.0.0.1:" + servidor.getAddress().getPort(), "sk_test_prueba", "cop", "pm_card_visa",
				Duration.ofMillis(500));
	}

	@AfterEach
	void detener() {
		servidor.stop(0);
	}

	private void intenciones(HttpExchange intercambio) throws IOException {
		String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		String ruta = intercambio.getRequestURI().getPath();
		peticiones.add(intercambio.getRequestMethod() + " " + ruta + " "
				+ intercambio.getRequestHeaders().getFirst("Idempotency-Key") + " "
				+ intercambio.getRequestHeaders().getFirst("Authorization") + " " + cuerpo);
		if (ruta.endsWith("/confirm")) {
			confirmaciones.incrementAndGet();
			esperar();
			if (codigoConfirmacion == 402 || codigoConfirmacion == 400) {
				responder(intercambio, codigoConfirmacion, errorConfirmacion);
			} else {
				responder(intercambio, codigoConfirmacion, "{\"id\":\"pi_1\",\"status\":\"succeeded\"}");
			}
		} else if ("GET".equals(intercambio.getRequestMethod())) {
			if (ruta.equals("/v1/payment_intents/pi_1")) {
				responder(intercambio, 200, "{\"id\":\"pi_1\",\"status\":\"" + estadoConsultado + "\"}");
			} else {
				responder(intercambio, 404, "{\"error\":{\"message\":\"No such payment_intent\"}}");
			}
		} else {
			responder(intercambio, 200, "{\"id\":\"pi_1\",\"status\":\"requires_confirmation\"}");
		}
	}

	/** Crear y confirmar tardan lo mismo; cuenta las confirmaciones que llegan al servidor. */
	private void demorarAmbosPasos(long milisegundos) {
		demoraMs = milisegundos;
		servidor.removeContext("/v1/payment_intents");
		servidor.createContext("/v1/payment_intents", intercambio -> {
			intercambio.getRequestBody().readAllBytes();
			boolean confirmar = intercambio.getRequestURI().getPath().endsWith("/confirm");
			if (confirmar) {
				confirmaciones.incrementAndGet();
			}
			esperar();
			responder(intercambio, 200,
					"{\"id\":\"pi_1\",\"status\":\"" + (confirmar ? "succeeded" : "requires_confirmation") + "\"}");
		});
	}

	private void esperar() {
		esperarMs(demoraMs);
	}

	/** Simula la latencia de Stripe sin Thread.sleep: espera un evento que nunca llega. */
	private static void esperarMs(long milisegundos) {
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

	@Test
	void cobraEnDosPasosConClavesDeIdempotenciaYMontoEnCentavos() {
		ResultadoPago resultado = adaptador.cobrar(new SolicitudCobro("reserva-7", MONTO, "Reserva 7"));

		assertThat(resultado).isEqualTo(ResultadoPago.aprobado("pi_1"));
		assertThat(peticiones).hasSize(2);
		assertThat(peticiones.get(0)).startsWith("POST /v1/payment_intents reserva-7:crear Bearer sk_test_prueba")
				.contains("amount=15000050", "currency=cop", "metadata%5Bclave_idempotencia%5D=reserva-7");
		assertThat(peticiones.get(1)).startsWith("POST /v1/payment_intents/pi_1/confirm reserva-7:confirmar");
	}

	@Test
	void unaTarjetaRechazadaEsUnPagoRechazado() {
		codigoConfirmacion = 402;

		ResultadoPago resultado = adaptador.cobrar(new SolicitudCobro("reserva-7", MONTO, "Reserva 7"));

		assertThat(resultado.estado()).isEqualTo(EstadoPago.RECHAZADO);
		assertThat(resultado.detalle()).isEqualTo("Your card was declined.");
	}

	@Test
	void siStripeNoRespondeATiempoElCobroQuedaEnDuda() {
		demoraMs = 1500;
		SolicitudCobro solicitud = new SolicitudCobro("reserva-7", MONTO, "Reserva 7");

		assertThatThrownBy(() -> adaptador.cobrar(solicitud)).isInstanceOf(PasarelaNoDisponibleException.class);
	}

	@Test
	void unErrorDelServidorTambienDejaElCobroEnDuda() {
		codigoConfirmacion = 500;
		SolicitudCobro solicitud = new SolicitudCobro("reserva-7", MONTO, "Reserva 7");

		assertThatThrownBy(() -> adaptador.cobrar(solicitud)).isInstanceOf(PasarelaNoDisponibleException.class);
	}

	@Test
	void consultarLeeElEstadoActualDelMismoPaymentIntent() {
		assertThat(adaptador.consultar("reserva-7", MONTO)).isEqualTo(ResultadoPago.aprobado("pi_1"));

		estadoConsultado = "requires_confirmation";
		assertThat(adaptador.consultar("reserva-7", MONTO).estado()).isEqualTo(EstadoPago.NO_COBRADO);
		assertThat(peticiones).filteredOn(peticion -> peticion.startsWith("POST /v1/payment_intents "))
				.allMatch(peticion -> peticion.contains("reserva-7:crear"));
		// El GET va al mismo PaymentIntent que devolvió la creación
		assertThat(peticiones).filteredOn(peticion -> peticion.startsWith("GET "))
				.hasSize(2).allMatch(peticion -> peticion.startsWith("GET /v1/payment_intents/pi_1 "));
	}

	@Test
	void devolverEnviaLaDevolucionDelCobro() {
		adaptador.devolver("pi_1", MONTO, "reserva-7");

		assertThat(peticiones).singleElement().asString()
				.startsWith("POST /v1/refunds reserva-7:devolucion ")
				.contains("payment_intent=pi_1", "amount=15000050");
	}

	@Test
	void exigeHttpsFueraDeEsteEquipo() {
		JsonMapper json = JsonMapper.builder().build();
		Duration timeout = Duration.ofSeconds(1);

		assertThatThrownBy(() -> new StripeAdapter(json, "http://api.stripe.com", "sk_test_x", "cop", "pm_card_visa",
				timeout)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void losDosPasosDelCobroCompartenElMismoLimiteDeTiempo() {
		// Cada paso solo tarda 300 ms, pero juntos superan el límite de 500 ms
		demorarAmbosPasos(300);
		SolicitudCobro solicitud = new SolicitudCobro("reserva-7", MONTO, "Reserva 7");

		assertThatThrownBy(() -> adaptador.cobrar(solicitud)).isInstanceOf(PasarelaNoDisponibleException.class);
		// La creación terminó bien: lo que se agotó fue el tiempo de la confirmación
		assertThat(confirmaciones).hasValue(1);
	}

	@Test
	void conTodoElPlazoLaMismaDemoraSiAlcanza() {
		demorarAmbosPasos(300);
		StripeAdapter conMasTiempo = new StripeAdapter(JsonMapper.builder().build(),
				"http://127.0.0.1:" + servidor.getAddress().getPort(), "sk_test_prueba", "cop", "pm_card_visa",
				Duration.ofSeconds(3));

		assertThat(conMasTiempo.cobrar(new SolicitudCobro("reserva-7", MONTO, "Reserva 7")).aprobado()).isTrue();
	}

	@Test
	void unErrorDeLaPeticionNoEsUnRechazoDeLaTarjeta() {
		codigoConfirmacion = 400;
		errorConfirmacion = "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"Parámetro inválido\"}}";
		SolicitudCobro solicitud = new SolicitudCobro("reserva-7", MONTO, "Reserva 7");

		assertThatThrownBy(() -> adaptador.cobrar(solicitud)).isInstanceOf(PasarelaNoDisponibleException.class);
	}

	@Test
	void unCuerpoQueNoTerminaDeLlegarTambienAgotaElPlazo() {
		servidor.createContext("/v1/lento", intercambio -> {
			intercambio.getRequestBody().readAllBytes();
			intercambio.sendResponseHeaders(200, 0);
			OutputStream salida = intercambio.getResponseBody();
			salida.write("{\"id\":".getBytes(StandardCharsets.UTF_8));
			salida.flush();
			esperarMs(1500);
			salida.write("\"pi_1\"}".getBytes(StandardCharsets.UTF_8));
			salida.close();
		});
		StripeAdapter lento = new StripeAdapter(JsonMapper.builder().build(),
				"http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1/lento", "sk_test_prueba", "cop",
				"pm_card_visa", Duration.ofMillis(500));
		SolicitudCobro solicitud = new SolicitudCobro("reserva-7", MONTO, "Reserva 7");

		assertThatThrownBy(() -> lento.cobrar(solicitud)).isInstanceOf(PasarelaNoDisponibleException.class);
	}

	@Test
	void soloAdmiteClavesDePruebaYMonedasConCentavos() {
		JsonMapper json = JsonMapper.builder().build();
		Duration timeout = Duration.ofSeconds(1);

		assertThatThrownBy(() -> new StripeAdapter(json, "https://api.stripe.com", "sk_live_123", "cop",
				"pm_card_visa", timeout)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new StripeAdapter(json, "https://api.stripe.com", "sk_test_123", "jpy",
				"pm_card_visa", timeout)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void sinClaveSecretaNoArranca() {
		JsonMapper json = JsonMapper.builder().build();
		Duration timeout = Duration.ofSeconds(1);

		assertThatThrownBy(() -> new StripeAdapter(json, "http://localhost", " ", "cop", "pm_card_visa", timeout))
				.isInstanceOf(IllegalStateException.class);
	}
}
