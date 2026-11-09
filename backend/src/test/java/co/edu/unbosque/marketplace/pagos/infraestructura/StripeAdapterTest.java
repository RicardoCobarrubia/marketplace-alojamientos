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
	private volatile int codigoConfirmacion = 200;
	private volatile String estadoConsultado = "succeeded";
	private volatile long demoraMs;
	private StripeAdapter adaptador;

	@BeforeEach
	void iniciar() throws IOException {
		servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		servidor.createContext("/v1/payment_intents", this::intenciones);
		servidor.createContext("/v1/refunds", intercambio -> responder(intercambio, 200, "{\"id\":\"re_1\"}"));
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
			esperar();
			if (codigoConfirmacion == 402) {
				responder(intercambio, 402, "{\"error\":{\"message\":\"Your card was declined.\"}}");
			} else {
				responder(intercambio, codigoConfirmacion, "{\"id\":\"pi_1\",\"status\":\"succeeded\"}");
			}
		} else if ("GET".equals(intercambio.getRequestMethod())) {
			responder(intercambio, 200, "{\"id\":\"pi_1\",\"status\":\"" + estadoConsultado + "\"}");
		} else {
			responder(intercambio, 200, "{\"id\":\"pi_1\",\"status\":\"requires_confirmation\"}");
		}
	}

	private void esperar() {
		try {
			Thread.sleep(demoraMs);
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
	}

	@Test
	void devolverEnviaLaDevolucionDelCobro() {
		adaptador.devolver("pi_1", MONTO, "reserva-7");
		// El servidor responde 200: no hay excepción
		assertThat(servidor.getAddress().getPort()).isPositive();
	}

	@Test
	void sinClaveSecretaNoArranca() {
		JsonMapper json = JsonMapper.builder().build();
		Duration timeout = Duration.ofSeconds(1);

		assertThatThrownBy(() -> new StripeAdapter(json, "http://localhost", " ", "cop", "pm_card_visa", timeout))
				.isInstanceOf(IllegalStateException.class);
	}
}
