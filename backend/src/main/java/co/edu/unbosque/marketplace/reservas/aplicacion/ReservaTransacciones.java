package co.edu.unbosque.marketplace.reservas.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento.EstadoAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.AlojamientoRepository;
import co.edu.unbosque.marketplace.comun.excepcion.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.comun.excepcion.ReglaNegocioException;
import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.comun.web.CorrelacionFilter;
import co.edu.unbosque.marketplace.cotizacion.aplicacion.CotizacionService;
import co.edu.unbosque.marketplace.cotizacion.dominio.Cotizacion;
import co.edu.unbosque.marketplace.disponibilidad.dominio.PeriodoReservado;
import co.edu.unbosque.marketplace.disponibilidad.dominio.RangoFechas;
import co.edu.unbosque.marketplace.disponibilidad.infraestructura.PeriodoDisponibleRepository;
import co.edu.unbosque.marketplace.disponibilidad.infraestructura.PeriodoReservadoRepository;
import co.edu.unbosque.marketplace.reservas.dominio.ClaveIdempotencia;
import co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;
import co.edu.unbosque.marketplace.reservas.infraestructura.ClaveIdempotenciaRepository;
import co.edu.unbosque.marketplace.reservas.infraestructura.ReservaRepository;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.infraestructura.UsuarioRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Las transacciones cortas del flujo de reserva (ADR-002). La llamada a la pasarela queda fuera
 * de ellas, en {@link ReservaService}, para no mantener bloqueos abiertos mientras se paga.
 * <ul>
 * <li>Transacción 1 ({@link #iniciar}): registra la clave de idempotencia, crea la reserva en
 * PENDIENTE_PAGO y retiene las fechas.</li>
 * <li>Transacción 2 ({@link #confirmar} o {@link #compensar}): deja la reserva en CONFIRMADA o
 * PAGO_FALLIDO, bloquea o libera las fechas, guarda la respuesta de la clave y el evento del
 * outbox, en un solo paso.</li>
 * </ul>
 */
@Service
public class ReservaTransacciones {

	private static final Logger log = LoggerFactory.getLogger(ReservaTransacciones.class);
	private static final String NO_EXISTE = "La reserva no existe.";

	private final ReservaRepository reservaRepository;
	private final ClaveIdempotenciaRepository claveRepository;
	private final AlojamientoRepository alojamientoRepository;
	private final UsuarioRepository usuarioRepository;
	private final PeriodoDisponibleRepository periodoDisponibleRepository;
	private final PeriodoReservadoRepository periodoReservadoRepository;
	private final CotizacionService cotizacionService;
	private final EventosReserva eventos;
	private final ObjectMapper json;
	private final Clock reloj;
	private final Duration retencion;

	public ReservaTransacciones(ReservaRepository reservaRepository, ClaveIdempotenciaRepository claveRepository,
			AlojamientoRepository alojamientoRepository, UsuarioRepository usuarioRepository,
			PeriodoDisponibleRepository periodoDisponibleRepository,
			PeriodoReservadoRepository periodoReservadoRepository, CotizacionService cotizacionService,
			EventosReserva eventos, ObjectMapper json, Clock reloj,
			@Value("${marketplace.reservas.retencion:PT10M}") Duration retencion) {
		this.reservaRepository = reservaRepository;
		this.claveRepository = claveRepository;
		this.alojamientoRepository = alojamientoRepository;
		this.usuarioRepository = usuarioRepository;
		this.periodoDisponibleRepository = periodoDisponibleRepository;
		this.periodoReservadoRepository = periodoReservadoRepository;
		this.cotizacionService = cotizacionService;
		this.eventos = eventos;
		this.json = json;
		this.reloj = reloj;
		this.retencion = retencion;
	}

	/** Lo que quedó después de la transacción 2. */
	public record Desenlace(RespuestaReserva respuesta, boolean requiereDevolucion) {
	}

	// ─────────────────────────────────────────
	// Transacción 1 — MA-66, MA-60
	// ─────────────────────────────────────────

	/**
	 * Registra la clave, valida la solicitud, crea la reserva en PENDIENTE_PAGO y retiene las
	 * fechas por 10 minutos.
	 *
	 * @throws org.springframework.dao.DataIntegrityViolationException si la clave ya existía
	 *         (reintento) o si las fechas se cruzan con otra reserva (ADR-003); quien llama
	 *         distingue los dos casos
	 */
	@Transactional
	public Reserva iniciar(Usuario huesped, String clave, String huella, ReservaDTO solicitud) {
		Instant ahora = reloj.instant();
		ClaveIdempotencia registro = claveRepository.saveAndFlush(
				new ClaveIdempotencia(huesped.getId(), clave, huella, ahora));

		RangoFechas rango = cotizacionService.validarFechas(solicitud.llegada(), solicitud.salida());
		Alojamiento alojamiento = alojamientoRepository.buscarParaReservar(solicitud.alojamientoId())
				.orElseThrow(() -> new RecursoNoEncontradoException("El alojamiento no existe."));
		validar(alojamiento, rango, solicitud.huespedes());
		Cotizacion cotizacion = cotizacionService.calcular(alojamiento, rango, solicitud.servicioIds());

		Reserva reserva = reservaRepository.save(new Reserva(usuarioRepository.getReferenceById(huesped.getId()),
				alojamiento, rango, solicitud.huespedes(), cotizacion, MDC.get(CorrelacionFilter.CLAVE_MDC), ahora));
		periodoReservadoRepository.liberarRetencionesVencidas(alojamiento.getId(), rango.llegada(), rango.salida(),
				ahora);
		periodoReservadoRepository.saveAndFlush(
				new PeriodoReservado(alojamiento, reserva.getId(), rango, ahora.plus(retencion), ahora));
		registro.asociar(reserva.getId());
		return reserva;
	}

	/** MA-66, escenarios 1 y 2. */
	private void validar(Alojamiento alojamiento, RangoFechas rango, int huespedes) {
		if (alojamiento.getEstado() != EstadoAlojamiento.ACTIVA) {
			throw new ReglaNegocioException("ALOJAMIENTO_NO_DISPONIBLE", "El alojamiento no está publicado.");
		}
		if (huespedes > alojamiento.getCapacidad()) {
			throw new SolicitudInvalidaException("HUESPEDES_INVALIDOS",
					"El alojamiento admite máximo " + alojamiento.getCapacidad() + " huéspedes.");
		}
		if (!periodoDisponibleRepository.algunoContiene(alojamiento.getId(), rango.llegada(), rango.salida())) {
			throw new ReglaNegocioException("FUERA_DE_PERIODO",
					"El anfitrión no tiene disponibles esas fechas.");
		}
	}

	// ─────────────────────────────────────────
	// Transacción 2 — MA-67, MA-68, MA-69
	// ─────────────────────────────────────────

	/**
	 * El pago se aprobó. Antes de confirmar se revalida que las fechas sigan retenidas para esta
	 * reserva (MA-69, escenario 1): si la retención venció y otra reserva las tomó, se compensa y
	 * el cobro debe devolverse.
	 * <p>
	 * Cuando hay que devolver, la reserva queda con el pago por verificar en esta misma
	 * transacción: si el proceso cae antes de pedir la devolución, el conciliador la retoma.
	 */
	@Transactional
	public Desenlace confirmar(Long reservaId, String referenciaPago) {
		Reserva reserva = bloquear(reservaId);
		if (reserva.getEstado().fueConfirmada() && referenciaPago.equals(reserva.getReferenciaPago())) {
			// Otro reintento ya la confirmó con este mismo cobro (y quizá ya se completó o canceló):
			// ese cobro pagó la reserva, así que no se devuelve aquí
			return new Desenlace(respuestaGuardada(reserva), false);
		}
		if (reserva.getEstado() != EstadoReserva.PENDIENTE_PAGO) {
			// El conciliador ya la resolvió (por ejemplo, venció la retención): el cobro sobra
			if (reserva.getEstado() == EstadoReserva.PAGO_FALLIDO) {
				reserva.marcarPagoPorVerificar(reloj.instant());
			}
			return new Desenlace(respuestaGuardada(reserva), true);
		}
		PeriodoReservado fechas = fechasDe(reservaId);
		if (!fechas.estaVigente(reloj.instant())) {
			// Se liberaron o la retención de 10 minutos ya venció: no se puede confirmar
			RespuestaReserva respuesta = fallar(reserva, fechas, HttpStatus.CONFLICT, "FECHAS_NO_DISPONIBLES",
					"Las fechas dejaron de estar disponibles mientras se procesaba el pago. El cobro se devolverá.",
					referenciaPago, true);
			return new Desenlace(respuesta, true);
		}
		Instant ahora = reloj.instant();
		reserva.confirmar(referenciaPago, ahora);
		fechas.bloquearDefinitivamente();
		eventos.publicar("RESERVA_CONFIRMADA", reserva);
		RespuestaReserva respuesta = new RespuestaReserva(HttpStatus.CREATED.value(),
				json.writeValueAsString(ReservaDTO.desde(reserva)));
		terminarClave(reservaId, respuesta, ahora);
		log.info("Reserva {} confirmada", reservaId);
		return new Desenlace(respuesta, false);
	}

	/**
	 * El pago falló, no respondió o la retención venció (compensación, SD-03.1): la reserva pasa
	 * a PAGO_FALLIDO y las fechas se liberan en la misma transacción.
	 * <p>
	 * Si la reserva ya no estaba pendiente (otro flujo la resolvió primero), no cambia nada y no
	 * hay que devolver. Si sí se compensa y hubo cobro, la devolución queda pendiente en esta
	 * misma transacción hasta que la pasarela la acepte.
	 *
	 * @param hayCobro la pasarela confirmó que sí cobró: hay que devolver
	 */
	@Transactional
	public Desenlace compensar(Long reservaId, HttpStatus estadoHttp, String codigo, String detalle,
			String referenciaPago, boolean porVerificar, boolean hayCobro) {
		Reserva reserva = bloquear(reservaId);
		if (reserva.getEstado() != EstadoReserva.PENDIENTE_PAGO) {
			return new Desenlace(respuestaGuardada(reserva), false);
		}
		RespuestaReserva respuesta = fallar(reserva, fechasDe(reservaId), estadoHttp, codigo, detalle, referenciaPago,
				porVerificar || hayCobro);
		return new Desenlace(respuesta, hayCobro);
	}

	private RespuestaReserva fallar(Reserva reserva, PeriodoReservado fechas, HttpStatus estadoHttp, String codigo,
			String detalle, String referenciaPago, boolean porVerificar) {
		Instant ahora = reloj.instant();
		reserva.marcarPagoFallido(detalle, referenciaPago, porVerificar, ahora);
		if (fechas.getLiberadoEn() == null) {
			fechas.liberar(ahora);
		}
		eventos.publicar("PAGO_FALLIDO", reserva);
		RespuestaReserva respuesta = new RespuestaReserva(estadoHttp.value(),
				problema(estadoHttp, codigo, detalle, reserva.getId()));
		terminarClave(reserva.getId(), respuesta, ahora);
		log.info("Reserva {} en PAGO_FALLIDO ({})", reserva.getId(), codigo);
		return respuesta;
	}

	// ─────────────────────────────────────────
	// Conciliación — MA-60, MA-68
	// ─────────────────────────────────────────

	/** Guarda la referencia del cobro preparado, antes de cobrar (ADR-004). */
	@Transactional
	public void registrarReferencia(Long reservaId, String referenciaPago) {
		bloquear(reservaId).registrarReferencia(referenciaPago, reloj.instant());
	}

	/** Cuenta el intento de devolución en su propia transacción y devuelve su número. */
	@Transactional
	public int siguienteIntentoDevolucion(Long reservaId) {
		return bloquear(reservaId).siguienteIntentoDevolucion(reloj.instant());
	}

	@Transactional
	public void marcarPagoVerificado(Long reservaId, String referenciaPago) {
		bloquear(reservaId).pagoVerificado(referenciaPago, reloj.instant());
	}

	@Transactional
	public void marcarPagoPorVerificar(Long reservaId) {
		Reserva reserva = bloquear(reservaId);
		if (!reserva.isPagoPorVerificar()) {
			reserva.marcarPagoPorVerificar(reloj.instant());
		}
	}

	@Transactional(readOnly = true)
	public List<Long> pendientesVencidas() {
		return reservaRepository.buscarPorEstadoCreadasAntesDe(EstadoReserva.PENDIENTE_PAGO,
				reloj.instant().minus(retencion));
	}

	@Transactional(readOnly = true)
	public List<Long> pagosPorVerificar() {
		return reservaRepository.buscarPagosPorVerificar();
	}

	// ─────────────────────────────────────────
	// Lecturas
	// ─────────────────────────────────────────

	@Transactional(readOnly = true)
	public Optional<ClaveIdempotencia> buscarClave(Long usuarioId, String clave) {
		return claveRepository.findByUsuarioIdAndClave(usuarioId, clave);
	}

	@Transactional(readOnly = true)
	public Optional<Reserva> buscar(Long reservaId) {
		return reservaRepository.findById(reservaId);
	}

	@Transactional(readOnly = true)
	public ReservaDTO obtener(Usuario usuario, Long reservaId) {
		return reservaRepository.findById(reservaId)
				.filter(reserva -> reserva.esVisiblePara(usuario))
				.map(ReservaDTO::desde)
				.orElseThrow(() -> new RecursoNoEncontradoException(NO_EXISTE));
	}

	// ─────────────────────────────────────────
	// Auxiliares
	// ─────────────────────────────────────────

	private Reserva bloquear(Long reservaId) {
		return reservaRepository.buscarParaActualizar(reservaId)
				.orElseThrow(() -> new RecursoNoEncontradoException(NO_EXISTE));
	}

	private PeriodoReservado fechasDe(Long reservaId) {
		return periodoReservadoRepository.bloquearPorReserva(reservaId)
				.orElseThrow(() -> new IllegalStateException("La reserva " + reservaId + " no tiene fechas retenidas."));
	}

	private void terminarClave(Long reservaId, RespuestaReserva respuesta, Instant ahora) {
		claveRepository.findByReservaId(reservaId)
				.ifPresent(clave -> clave.terminar(respuesta.codigoHttp(), respuesta.cuerpo(), ahora));
	}

	/** Para un flujo que llega tarde: responde lo que ya quedó guardado. */
	private RespuestaReserva respuestaGuardada(Reserva reserva) {
		return claveRepository.findByReservaId(reserva.getId())
				.filter(clave -> clave.getCodigoHttp() != null)
				.map(clave -> new RespuestaReserva(clave.getCodigoHttp(), clave.getRespuesta()))
				.orElseGet(() -> new RespuestaReserva(HttpStatus.OK.value(),
						json.writeValueAsString(ReservaDTO.desde(reserva))));
	}

	/** Mismo formato que ManejadorGlobalExcepciones (Problem Details), con el número de reserva. */
	String problema(HttpStatus estado, String codigo, String detalle, Long reservaId) {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("type", "about:blank");
		cuerpo.put("title", estado.getReasonPhrase());
		cuerpo.put("status", estado.value());
		cuerpo.put("detail", detalle);
		cuerpo.put("codigo", codigo);
		cuerpo.put("reservaId", reservaId);
		cuerpo.put("correlationId", MDC.get(CorrelacionFilter.CLAVE_MDC));
		return json.writeValueAsString(cuerpo);
	}
}
