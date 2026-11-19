package co.edu.unbosque.marketplace.reservas.aplicacion;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import co.edu.unbosque.marketplace.comun.excepcion.ReglaNegocioException;
import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.pagos.dominio.PasarelaNoDisponibleException;
import co.edu.unbosque.marketplace.pagos.dominio.PasarelaPagos;
import co.edu.unbosque.marketplace.pagos.dominio.ResultadoPago;
import co.edu.unbosque.marketplace.pagos.dominio.SolicitudCobro;
import co.edu.unbosque.marketplace.reservas.aplicacion.ReservaTransacciones.Desenlace;
import co.edu.unbosque.marketplace.reservas.dominio.ClaveIdempotencia;
import co.edu.unbosque.marketplace.reservas.dominio.ClaveIdempotencia.EstadoClave;
import co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;

/**
 * Flujo de reserva con pago (ADR-002, SD-01 a SD-04): transacción 1, cobro fuera de
 * transacción con límite de tiempo y transacción 2 o compensación.
 * <ul>
 * <li>Pago aprobado: CONFIRMADA (201).</li>
 * <li>Pago rechazado: PAGO_FALLIDO y fechas liberadas (402).</li>
 * <li>Pasarela sin respuesta: se consulta el estado real del cobro; si sí cobró se pide la
 * devolución. La reserva queda en PAGO_FALLIDO con un error controlado (504).</li>
 * <li>Reintento con la misma clave: la respuesta guardada, o 202 si sigue en proceso (ADR-005).</li>
 * </ul>
 * Lo que quede pendiente si el proceso cae lo resuelve {@link ConciliadorReservas}.
 */
@Service
public class ReservaService {

	private static final Logger log = LoggerFactory.getLogger(ReservaService.class);
	private static final int MAXIMO_CLAVE = 100;
	private static final String SIN_RESPUESTA = "PASARELA_SIN_RESPUESTA";
	private static final String DETALLE_SIN_RESPUESTA = "La pasarela de pagos no respondió a tiempo.";
	private static final String RETENCION_VENCIDA = "RETENCION_VENCIDA";
	private static final String DETALLE_RETENCION_VENCIDA = "La retención de las fechas venció sin confirmar el pago.";

	private final ReservaTransacciones transacciones;
	private final PasarelaPagos pasarela;
	private final Clock reloj;
	private final Duration esperaRecuperacion;

	public ReservaService(ReservaTransacciones transacciones, PasarelaPagos pasarela, Clock reloj,
			@Value("${marketplace.pagos.timeout:PT10S}") Duration timeoutPasarela) {
		this.transacciones = transacciones;
		this.pasarela = pasarela;
		this.reloj = reloj;
		// Pasado el timeout más un margen, una reserva en curso quedó huérfana: el reintento la retoma
		this.esperaRecuperacion = timeoutPasarela.multipliedBy(2).plusSeconds(5);
	}

	// ─────────────────────────────────────────
	// Reservar — MA-66, MA-67, MA-68, MA-69
	// ─────────────────────────────────────────

	public RespuestaReserva reservar(Usuario huesped, String clave, ReservaDTO solicitud) {
		if (clave == null || clave.isBlank() || clave.length() > MAXIMO_CLAVE) {
			throw new SolicitudInvalidaException("CLAVE_IDEMPOTENCIA_REQUERIDA",
					"Envía la cabecera Idempotency-Key con un valor único por intento de reserva (máximo 100 caracteres).");
		}
		String huella = huella(solicitud);
		Reserva reserva;
		try {
			reserva = transacciones.iniciar(huesped, clave, huella, solicitud);
		} catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
			// Clave repetida, fechas tomadas por otra reserva (ADR-003) o, con solicitudes
			// simultáneas, un interbloqueo que PostgreSQL resolvió abortando esta transacción
			Optional<ClaveIdempotencia> previa = transacciones.buscarClave(huesped.getId(), clave);
			if (previa.isPresent()) {
				return reintento(previa.get(), huella);
			}
			// No era la clave: la restricción de exclusión rechazó las fechas (ADR-003, SD-02)
			throw new ReglaNegocioException("FECHAS_NO_DISPONIBLES", "Las fechas ya no están disponibles.");
		}
		log.info("Reserva {} creada en PENDIENTE_PAGO", reserva.getId());
		return cobrar(reserva.getId(), reserva.getTotal());
	}

	/**
	 * Prepara el cobro, guarda su referencia y cobra (ADR-004). Si la pasarela no responde al
	 * preparar, no se cobró nada: se compensa sin dudas.
	 */
	private RespuestaReserva cobrar(Long reservaId, BigDecimal total) {
		SolicitudCobro solicitud = new SolicitudCobro(clavePago(reservaId), total, "Reserva " + reservaId);
		String referencia;
		try {
			referencia = pasarela.preparar(solicitud);
		} catch (PasarelaNoDisponibleException e) {
			log.warn("La pasarela no respondió al preparar el cobro de la reserva {}: {}", reservaId, e.getMessage());
			return transacciones.compensar(reservaId, HttpStatus.GATEWAY_TIMEOUT, SIN_RESPUESTA,
					DETALLE_SIN_RESPUESTA, null, false, false).respuesta();
		}
		transacciones.registrarReferencia(reservaId, referencia);
		ResultadoPago resultado;
		try {
			resultado = pasarela.cobrar(referencia, solicitud);
		} catch (PasarelaNoDisponibleException e) {
			log.warn("La pasarela no respondió al cobrar la reserva {}: {}", reservaId, e.getMessage());
			return resolverSinRespuesta(reservaId, referencia, total);
		}
		return aplicar(reservaId, total, resultado);
	}

	/** SD-01 y SD-03: aplica el resultado del cobro en la transacción 2. */
	private RespuestaReserva aplicar(Long reservaId, BigDecimal total, ResultadoPago resultado) {
		if (resultado.aprobado()) {
			Desenlace desenlace = transacciones.confirmar(reservaId, resultado.referencia());
			if (desenlace.requiereDevolucion()) {
				devolver(reservaId, resultado.referencia(), total);
			}
			return desenlace.respuesta();
		}
		return transacciones.compensar(reservaId, HttpStatus.PAYMENT_REQUIRED, "PAGO_RECHAZADO",
				"La pasarela rechazó el pago" + (resultado.detalle() == null ? "." : ": " + resultado.detalle()),
				resultado.referencia(), false, false).respuesta();
	}

	/**
	 * SD-03: la pasarela no respondió al cobrar. Se consulta el estado real por la referencia; si
	 * sí cobró, se pide la devolución. Si dice que no, la reserva queda por verificar una vez más,
	 * por si el cobro todavía se estaba procesando; si tampoco responde, el conciliador lo revisa.
	 */
	private RespuestaReserva resolverSinRespuesta(Long reservaId, String referencia, BigDecimal total) {
		ResultadoPago real;
		try {
			real = pasarela.consultar(referencia);
		} catch (PasarelaNoDisponibleException e) {
			return transacciones.compensar(reservaId, HttpStatus.GATEWAY_TIMEOUT, SIN_RESPUESTA,
					DETALLE_SIN_RESPUESTA, null, true, false).respuesta();
		}
		Desenlace desenlace = transacciones.compensar(reservaId, HttpStatus.GATEWAY_TIMEOUT, SIN_RESPUESTA,
				DETALLE_SIN_RESPUESTA, null, !real.aprobado(), real.aprobado());
		if (desenlace.requiereDevolucion()) {
			devolver(reservaId, referencia, total);
		}
		return desenlace.respuesta();
	}

	/**
	 * MA-68, escenario 2. Cada intento se cuenta antes de llamar a la pasarela, así un reintento
	 * usa una clave nueva. Si falla, la reserva queda por verificar y el conciliador lo reintenta.
	 */
	private void devolver(Long reservaId, String referencia, BigDecimal total) {
		try {
			int intento = transacciones.siguienteIntentoDevolucion(reservaId);
			pasarela.devolver(referencia, total, clavePago(reservaId), intento);
			log.info("Devolución solicitada para la reserva {}", reservaId);
			transacciones.marcarPagoVerificado(reservaId, referencia);
		} catch (PasarelaNoDisponibleException e) {
			log.warn("No se pudo solicitar la devolución de la reserva {}: {}", reservaId, e.getMessage());
			transacciones.marcarPagoPorVerificar(reservaId);
		}
	}

	// ─────────────────────────────────────────
	// Reintentos — ADR-005, SD-04
	// ─────────────────────────────────────────

	private RespuestaReserva reintento(ClaveIdempotencia previa, String huella) {
		if (!previa.mismaSolicitud(huella)) {
			throw new ReglaNegocioException("IDEMPOTENCIA_CONFLICTO",
					"Esa clave de idempotencia ya se usó con otros datos de reserva.");
		}
		if (previa.getEstado() == EstadoClave.TERMINADA) {
			return new RespuestaReserva(previa.getCodigoHttp(), previa.getRespuesta());
		}
		Optional<Reserva> reserva = Optional.ofNullable(previa.getReservaId()).flatMap(transacciones::buscar);
		if (reserva.isPresent() && huerfana(reserva.get())) {
			// ADR-002: el proceso cayó entre el cobro y la transacción 2; se consulta y se completa
			return recuperar(reserva.get());
		}
		if (reserva.isPresent() && reserva.get().getEstado() != EstadoReserva.PENDIENTE_PAGO) {
			// Se resolvió entre las dos lecturas: la respuesta ya quedó guardada en la clave
			Optional<RespuestaReserva> guardada = transacciones.buscarClave(previa.getUsuarioId(), previa.getClave())
					.filter(clave -> clave.getEstado() == EstadoClave.TERMINADA)
					.map(clave -> new RespuestaReserva(clave.getCodigoHttp(), clave.getRespuesta()));
			if (guardada.isPresent()) {
				return guardada.get();
			}
		}
		return new RespuestaReserva(HttpStatus.ACCEPTED.value(), enProceso(previa.getReservaId()));
	}

	private boolean huerfana(Reserva reserva) {
		return reserva.getEstado() == EstadoReserva.PENDIENTE_PAGO
				&& reserva.getCreadoEn().plus(esperaRecuperacion).isBefore(reloj.instant());
	}

	/**
	 * Retoma una reserva que quedó en PENDIENTE_PAGO: si el cobro se hizo, la confirma; si no,
	 * la compensa. La usa el reintento (ADR-002).
	 */
	RespuestaReserva recuperar(Reserva reserva) {
		Long reservaId = reserva.getId();
		if (reserva.getReferenciaPago() == null) {
			// Nunca llegó a cobrarse: sin referencia no hay cobro
			return transacciones.compensar(reservaId, HttpStatus.PAYMENT_REQUIRED, "PAGO_NO_REALIZADO",
					"El pago no se completó.", null, false, false).respuesta();
		}
		ResultadoPago real;
		try {
			real = pasarela.consultar(reserva.getReferenciaPago());
		} catch (PasarelaNoDisponibleException e) {
			return transacciones.compensar(reservaId, HttpStatus.GATEWAY_TIMEOUT, SIN_RESPUESTA,
					"No se pudo confirmar el pago con la pasarela.", null, true, false).respuesta();
		}
		if (real.aprobado()) {
			return aplicar(reservaId, reserva.getTotal(), real);
		}
		// Queda por verificar: si el intento original todavía estaba cobrando, el conciliador
		// encontrará ese cobro tardío y lo devolverá
		return transacciones.compensar(reservaId, HttpStatus.PAYMENT_REQUIRED, "PAGO_NO_REALIZADO",
				"El pago no se completó.", null, true, false).respuesta();
	}

	/**
	 * La retención de 10 minutos venció sin confirmar (MA-60, escenario 2): si alcanzó a cobrarse
	 * se devuelve, y la reserva queda en PAGO_FALLIDO con las fechas libres.
	 */
	void vencer(Reserva reserva) {
		Long reservaId = reserva.getId();
		String referencia = reserva.getReferenciaPago();
		if (referencia == null) {
			transacciones.compensar(reservaId, HttpStatus.CONFLICT, RETENCION_VENCIDA, DETALLE_RETENCION_VENCIDA,
					null, false, false);
			return;
		}
		ResultadoPago real;
		try {
			real = pasarela.consultar(referencia);
		} catch (PasarelaNoDisponibleException e) {
			transacciones.compensar(reservaId, HttpStatus.GATEWAY_TIMEOUT, RETENCION_VENCIDA,
					DETALLE_RETENCION_VENCIDA, null, true, false);
			return;
		}
		// Si mientras tanto otro flujo la confirmó, compensar no cambia nada y no se devuelve
		Desenlace desenlace = transacciones.compensar(reservaId, HttpStatus.CONFLICT, RETENCION_VENCIDA,
				DETALLE_RETENCION_VENCIDA, null, !real.aprobado(), real.aprobado());
		if (desenlace.requiereDevolucion()) {
			devolver(reservaId, referencia, reserva.getTotal());
		}
	}

	/** Un pago quedó en duda: si hubo cobro, se pide la devolución (MA-68, escenario 3). */
	void verificarPago(Reserva reserva) {
		String referencia = reserva.getReferenciaPago();
		if (referencia == null) {
			// Sin referencia nunca se cobró
			transacciones.marcarPagoVerificado(reserva.getId(), null);
			return;
		}
		ResultadoPago real = pasarela.consultar(referencia);
		if (real.aprobado() && reserva.getEstado() != EstadoReserva.CONFIRMADA) {
			int intento = transacciones.siguienteIntentoDevolucion(reserva.getId());
			pasarela.devolver(referencia, reserva.getTotal(), clavePago(reserva.getId()), intento);
			log.info("Devolución solicitada para la reserva {} (pago verificado)", reserva.getId());
		}
		transacciones.marcarPagoVerificado(reserva.getId(), referencia);
	}

	// ─────────────────────────────────────────
	// Auxiliares
	// ─────────────────────────────────────────

	/** La clave que va a la pasarela es única por reserva; así dos usuarios nunca la comparten. */
	static String clavePago(Long reservaId) {
		return "reserva-" + reservaId;
	}

	/** Resume los datos que identifican la solicitud: alojamiento, fechas, huéspedes y servicios. */
	static String huella(ReservaDTO solicitud) {
		Set<Long> servicios = solicitud.servicioIds() == null ? Set.of() : new TreeSet<>(solicitud.servicioIds());
		String datos = solicitud.alojamientoId() + "|" + solicitud.llegada() + "|" + solicitud.salida() + "|"
				+ solicitud.huespedes() + "|" + servicios;
		try {
			byte[] resumen = MessageDigest.getInstance("SHA-256").digest(datos.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(resumen);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no disponible", e);
		}
	}

	private String enProceso(Long reservaId) {
		return "{\"reservaId\":" + reservaId + ",\"estado\":\"PENDIENTE_PAGO\","
				+ "\"mensaje\":\"La reserva se está procesando. Consulta su estado en unos segundos.\"}";
	}
}
