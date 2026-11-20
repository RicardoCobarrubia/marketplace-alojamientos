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
 * <ul>
 * <li>{@link #preparar}: crea el PaymentIntent sin confirmar (no cobra). Su id es la referencia
 * que la reserva guarda.</li>
 * <li>{@link #cobrar}: confirma ese PaymentIntent; aquí se cobra.</li>
 * <li>{@link #consultar}: lee el PaymentIntent por su id. No depende de las claves de
 * idempotencia, que Stripe borra a las 24 horas.</li>
 * <li>{@link #devolver}: antes de pedir la devolución revisa si ya existe una para ese cobro.
 * Cada intento usa su propia clave, porque Stripe repite la respuesta guardada de una clave,
 * incluso si fue un error.</li>
 * </ul>
 */
public class StripeAdapter implements PasarelaPagos {

	private static final Logger log = LoggerFactory.getLogger(StripeAdapter.class);
	private static final String EXITOSO = "succeeded";
	private static final String ESTADO = "status";
	private static final String MONTO = "amount";
	/** Estados en que Stripe dejó el PaymentIntent tras un intento de cobro rechazado. */
	private static final Set<String> COBRO_RECHAZADO = Set.of("requires_payment_method", "canceled");
	/** Estados de una devolución que ya está hecha o en camino: ese monto no se vuelve a pedir. */
	private static final Set<String> DEVOLUCION_VIGENTE = Set.of(EXITOSO, "pending", "requires_action");
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

	@Override
	public String preparar(SolicitudCobro solicitud) {
		Map<String, String> datos = new LinkedHashMap<>();
		datos.put(MONTO, Long.toString(centavos(solicitud.monto())));
		datos.put("currency", moneda);
		datos.put("payment_method_types[]", "card");
		datos.put("payment_method", metodoPago);
		datos.put("description", solicitud.descripcion());
		datos.put("metadata[clave_idempotencia]", solicitud.claveIdempotencia());
		Respuesta creacion = enviar("POST", "/v1/payment_intents", datos, solicitud.claveIdempotencia() + ":crear",
				limite());
		verificar(creacion);
		return creacion.cuerpo().path("id").asString();
	}

	@Override
	public ResultadoPago cobrar(String referencia, SolicitudCobro solicitud) {
		Respuesta confirmacion = enviar("POST", "/v1/payment_intents/" + codificar(referencia) + "/confirm", Map.of(),
				solicitud.claveIdempotencia() + ":confirmar", limite());
		JsonNode error = confirmacion.cuerpo().path("error");
		if (confirmacion.estado() == 402 || "card_error".equals(error.path("type").asString(""))) {
			// Stripe rechazó la tarjeta. Otros errores (parámetros, clave...) dejan el cobro en duda
			return ResultadoPago.rechazado(referencia, error.path("message").asString(""));
		}
		verificar(confirmacion);
		String estado = confirmacion.cuerpo().path(ESTADO).asString("");
		if (EXITOSO.equals(estado)) {
			return ResultadoPago.aprobado(referencia);
		}
		if (COBRO_RECHAZADO.contains(estado)) {
			return ResultadoPago.rechazado(referencia, estado);
		}
		// processing, requires_action o un estado desconocido: no se sabe si cobró, se consulta después
		throw PasarelaNoDisponibleException.conRespuesta("Stripe dejó el cobro en estado '" + estado + "'.");
	}

	@Override
	public ResultadoPago consultar(String referencia) {
		Respuesta actual = enviar("GET", "/v1/payment_intents/" + codificar(referencia), Map.of(), null, limite());
		verificar(actual);
		return EXITOSO.equals(actual.cuerpo().path(ESTADO).asString(""))
				? ResultadoPago.aprobado(referencia)
				: ResultadoPago.noCobrado();
	}

	@Override
	public void devolver(String referencia, BigDecimal monto, String claveIdempotencia, int intento) {
		Respuesta existentes = enviar("GET", "/v1/refunds?limit=100&payment_intent=" + codificar(referencia),
				Map.of(), null, limite());
		verificar(existentes);
		if (existentes.cuerpo().path("has_more").asBoolean(false)) {
			// No se ven todas las devoluciones: sin el saldo exacto no se pide nada y queda por verificar
			throw PasarelaNoDisponibleException.conRespuesta("El cobro " + referencia + " tiene demasiadas devoluciones.");
		}
		long yaDevuelto = 0;
		for (JsonNode devolucion : existentes.cuerpo().path("data")) {
			if (DEVOLUCION_VIGENTE.contains(devolucion.path(ESTADO).asString(""))) {
				yaDevuelto += devolucion.path(MONTO).asLong(0);
			}
		}
		long pendiente = centavos(monto) - yaDevuelto;
		if (pendiente <= 0) {
			log.info("El cobro {} ya está devuelto o en proceso de devolución", referencia);
			return;
		}
		Map<String, String> datos = new LinkedHashMap<>();
		datos.put("payment_intent", referencia);
		datos.put(MONTO, Long.toString(pendiente));
		verificar(enviar("POST", "/v1/refunds", datos, claveIdempotencia + ":devolucion:" + intento, limite()));
		log.info("Devolución solicitada a Stripe (cobro {}, intento {})", referencia, intento);
	}

	private Instant limite() {
		return Instant.now().plus(timeout);
	}

	private Respuesta enviar(String metodo, String ruta, Map<String, String> datos, String claveIdempotencia,
			Instant limite) {
		Duration restante = Duration.between(Instant.now(), limite);
		if (restante.isNegative() || restante.isZero()) {
			throw PasarelaNoDisponibleException.conRespuesta("Se agotó el tiempo para hablar con Stripe.");
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
			// La petición terminó, aunque la respuesta no se pueda leer
			throw PasarelaNoDisponibleException.conRespuesta("Stripe respondió algo que no es JSON: " + e.getMessage());
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
			throw PasarelaNoDisponibleException.conRespuesta("Stripe respondió " + respuesta.estado() + ": "
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
	private static long centavos(BigDecimal monto) {
		return monto.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
	}

	private record Respuesta(int estado, JsonNode cuerpo) {
	}
}
