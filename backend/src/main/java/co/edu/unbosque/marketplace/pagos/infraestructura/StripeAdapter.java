package co.edu.unbosque.marketplace.pagos.infraestructura;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.edu.unbosque.marketplace.pagos.dominio.PasarelaNoDisponibleException;
import co.edu.unbosque.marketplace.pagos.dominio.PasarelaPagos;
import co.edu.unbosque.marketplace.pagos.dominio.ResultadoPago;
import co.edu.unbosque.marketplace.pagos.dominio.SolicitudCobro;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Adaptador de Stripe en modo de pruebas (ADR-006). Es la única clase que conoce Stripe: cambiar
 * de proveedor es escribir otro adaptador.
 * <p>
 * El cobro se hace en dos pasos para poder consultar su estado real después de un timeout
 * (ADR-004):
 * <ol>
 * <li>Crear el PaymentIntent sin confirmar (no cobra) con la clave {@code <clave>:crear}.</li>
 * <li>Confirmarlo con la clave {@code <clave>:confirmar}: aquí se cobra.</li>
 * </ol>
 * Para consultar se repite el paso 1 con la misma clave: Stripe devuelve el mismo
 * PaymentIntent sin crear otro, y se lee su estado actual.
 */
public class StripeAdapter implements PasarelaPagos {

	private static final Logger log = LoggerFactory.getLogger(StripeAdapter.class);
	private static final String EXITOSO = "succeeded";
	/** Monedas sin decimales en Stripe: el adaptador solo maneja monedas con centavos, como COP. */
	private static final Set<String> SIN_DECIMALES = Set.of("bif", "clp", "djf", "gnf", "jpy", "kmf", "krw", "mga",
			"pyg", "rwf", "ugx", "vnd", "vuv", "xaf", "xof", "xpf");

	private final HttpClient http;
	private final ObjectMapper json;
	private final String urlBase;
	private final String claveSecreta;
	private final String moneda;
	private final String metodoPago;
	private final Duration timeout;

	/** Lo crea {@link PagosConfig} cuando {@code marketplace.pagos.proveedor=stripe}. */
	public StripeAdapter(ObjectMapper json, String urlBase, String claveSecreta, String moneda, String metodoPago,
			Duration timeout) {
		if (claveSecreta == null || claveSecreta.isBlank()) {
			throw new IllegalStateException("Falta marketplace.pagos.stripe.clave-secreta (variable STRIPE_SECRET_KEY).");
		}
		if (!claveSecreta.startsWith("sk_test_") && !claveSecreta.startsWith("rk_test_")) {
			// El proyecto solo simula pagos: una clave de modo activo podría cobrar de verdad
			throw new IllegalStateException("Solo se admiten claves de Stripe en modo prueba (sk_test_...).");
		}
		if (SIN_DECIMALES.contains(moneda.toLowerCase())) {
			throw new IllegalStateException("La moneda " + moneda + " no tiene centavos; el adaptador no la admite.");
		}
		exigirHttps(urlBase);
		this.json = json;
		this.urlBase = urlBase;
		this.claveSecreta = claveSecreta;
		this.moneda = moneda;
		this.metodoPago = metodoPago;
		this.timeout = timeout;
		this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
	}

	/**
	 * Los dos pasos comparten el mismo límite de tiempo ({@code marketplace.pagos.timeout}): la
	 * confirmación solo usa lo que dejó la creación.
	 */
	@Override
	public ResultadoPago cobrar(SolicitudCobro solicitud) {
		Instant limite = Instant.now().plus(timeout);
		String id = crear(solicitud.claveIdempotencia(), solicitud.monto(), limite);
		Respuesta confirmacion = enviar("POST", "/v1/payment_intents/" + id + "/confirm", Map.of(),
				solicitud.claveIdempotencia() + ":confirmar", limite);
		JsonNode error = confirmacion.cuerpo().path("error");
		if (confirmacion.estado() == 402 || "card_error".equals(error.path("type").asString(""))) {
			// Stripe rechazó la tarjeta. Otros errores (parámetros, clave...) dejan el cobro en duda
			return ResultadoPago.rechazado(id, error.path("message").asString(""));
		}
		verificar(confirmacion);
		String estado = confirmacion.cuerpo().path("status").asString("");
		return EXITOSO.equals(estado) ? ResultadoPago.aprobado(id) : ResultadoPago.rechazado(id, estado);
	}

	@Override
	public ResultadoPago consultar(String claveIdempotencia, BigDecimal monto) {
		Instant limite = Instant.now().plus(timeout);
		String id = crear(claveIdempotencia, monto, limite);
		Respuesta actual = enviar("GET", "/v1/payment_intents/" + id, Map.of(), null, limite);
		verificar(actual);
		return EXITOSO.equals(actual.cuerpo().path("status").asString(""))
				? ResultadoPago.aprobado(id)
				: ResultadoPago.noCobrado();
	}

	@Override
	public void devolver(String referencia, BigDecimal monto, String claveIdempotencia) {
		Map<String, String> datos = new LinkedHashMap<>();
		datos.put("payment_intent", referencia);
		datos.put("amount", centavos(monto));
		verificar(enviar("POST", "/v1/refunds", datos, claveIdempotencia + ":devolucion", Instant.now().plus(timeout)));
		log.info("Devolución solicitada a Stripe (cobro {})", referencia);
	}

	/** Crea el PaymentIntent sin confirmar. Con la misma clave, Stripe devuelve el mismo. */
	private String crear(String claveIdempotencia, BigDecimal monto, Instant limite) {
		Map<String, String> datos = new LinkedHashMap<>();
		datos.put("amount", centavos(monto));
		datos.put("currency", moneda);
		datos.put("payment_method_types[]", "card");
		datos.put("payment_method", metodoPago);
		datos.put("description", "Reserva " + claveIdempotencia);
		datos.put("metadata[clave_idempotencia]", claveIdempotencia);
		Respuesta creacion = enviar("POST", "/v1/payment_intents", datos, claveIdempotencia + ":crear", limite);
		verificar(creacion);
		return creacion.cuerpo().path("id").asString();
	}

	private Respuesta enviar(String metodo, String ruta, Map<String, String> datos, String claveIdempotencia,
			Instant limite) {
		Duration restante = Duration.between(Instant.now(), limite);
		if (restante.isNegative() || restante.isZero()) {
			throw new PasarelaNoDisponibleException("Se agotó el tiempo para hablar con Stripe.");
		}
		HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(urlBase + ruta))
				.timeout(restante)
				.header("Authorization", "Bearer " + claveSecreta);
		if (claveIdempotencia != null) {
			peticion.header("Idempotency-Key", claveIdempotencia);
		}
		if ("POST".equals(metodo)) {
			peticion.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(formulario(datos)));
		} else {
			peticion.GET();
		}
		// El límite cubre la respuesta completa, incluido el cuerpo, no solo las cabeceras
		CompletableFuture<HttpResponse<String>> envio = http.sendAsync(peticion.build(),
				HttpResponse.BodyHandlers.ofString());
		try {
			HttpResponse<String> respuesta = envio.get(Math.max(1, restante.toMillis()), TimeUnit.MILLISECONDS);
			return new Respuesta(respuesta.statusCode(), json.readTree(respuesta.body()));
		} catch (TimeoutException e) {
			envio.cancel(true);
			throw new PasarelaNoDisponibleException("Stripe no respondió a tiempo.", e);
		} catch (ExecutionException e) {
			throw new PasarelaNoDisponibleException("Falló la llamada a Stripe.", e.getCause());
		} catch (JacksonException e) {
			throw new PasarelaNoDisponibleException("Stripe respondió algo que no es JSON.", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new PasarelaNoDisponibleException("Se interrumpió la llamada a Stripe.", e);
		}
	}

	/** La clave secreta solo viaja cifrada; HTTP se admite únicamente hacia este mismo equipo (pruebas). */
	private static void exigirHttps(String urlBase) {
		URI uri = URI.create(urlBase);
		boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
		if (!"https".equals(uri.getScheme()) && !local) {
			throw new IllegalStateException("La URL de Stripe debe usar HTTPS: " + urlBase);
		}
	}

	/** Un error que no es de la tarjeta (5xx, clave inválida...) deja el cobro en duda. */
	private static void verificar(Respuesta respuesta) {
		if (respuesta.estado() >= 300) {
			throw new PasarelaNoDisponibleException("Stripe respondió " + respuesta.estado() + ": "
					+ respuesta.cuerpo().path("error").path("message").asString(""));
		}
	}

	private static String formulario(Map<String, String> datos) {
		return datos.entrySet().stream()
				.map(dato -> codificar(dato.getKey()) + "=" + codificar(dato.getValue()))
				.collect(Collectors.joining("&"));
	}

	private static String codificar(String texto) {
		return URLEncoder.encode(texto, StandardCharsets.UTF_8);
	}

	/** Stripe recibe los montos en la unidad mínima de la moneda (centavos). */
	private static String centavos(BigDecimal monto) {
		return monto.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString();
	}

	private record Respuesta(int estado, JsonNode cuerpo) {
	}
}
