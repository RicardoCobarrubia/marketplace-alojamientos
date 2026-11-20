package co.edu.unbosque.marketplace.reservas.dominio;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.comun.excepcion.ReglaNegocioException;
import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.cotizacion.dominio.Cotizacion;
import co.edu.unbosque.marketplace.disponibilidad.dominio.RangoFechas;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Reserva de un huésped (MA-66). Guarda una copia de la cotización y de la política de
 * cancelación: lo que cambie después en el alojamiento no la afecta.
 * <p>
 * El estado no se escribe directamente: solo cambia con las operaciones de transición
 * ({@link #confirmar}, {@link #marcarPagoFallido}, {@link #cancelar}, {@link #completar}), que
 * rechazan las no permitidas por {@link EstadoReserva} (patrón State, ADR-004; MA-71).
 */
@Entity
@Table(name = "reserva")
public class Reserva {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "huesped_id", nullable = false, updatable = false)
	private Usuario huesped;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "alojamiento_id", nullable = false, updatable = false)
	private Alojamiento alojamiento;

	@Column(name = "fecha_llegada", nullable = false, updatable = false)
	private LocalDate fechaLlegada;

	@Column(name = "fecha_salida", nullable = false, updatable = false)
	private LocalDate fechaSalida;

	@Column(nullable = false, updatable = false)
	private int huespedes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoReserva estado;

	// ─────────────────────────────────────────
	// Copia de la cotización (MA-63)
	// ─────────────────────────────────────────

	@Column(nullable = false, updatable = false)
	private int noches;

	@Column(name = "precio_noche", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal precioNoche;

	@Column(nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal subtotal;

	@Column(name = "total_descuentos", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal totalDescuentos;

	@Column(nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal limpieza;

	@Column(name = "total_servicios", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal totalServicios;

	@Column(name = "porcentaje_tarifa_servicio", nullable = false, updatable = false, precision = 5, scale = 2)
	private BigDecimal porcentajeTarifaServicio;

	@Column(name = "tarifa_servicio", nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal tarifaServicio;

	@Column(nullable = false, updatable = false, precision = 12, scale = 2)
	private BigDecimal total;

	@OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	private List<ReservaServicio> servicios = new ArrayList<>();

	// ─────────────────────────────────────────
	// Política de cancelación vigente al reservar (MA-72)
	// ─────────────────────────────────────────

	@Column(name = "politica_cancelacion_id", nullable = false, updatable = false)
	private Short politicaCancelacionId;

	@Column(name = "politica_cancelacion_codigo", nullable = false, updatable = false, length = 40)
	private String politicaCancelacionCodigo;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "politica_cancelacion", nullable = false, updatable = false, columnDefinition = "jsonb")
	private String politicaCancelacion;

	// ─────────────────────────────────────────
	// Pago
	// ─────────────────────────────────────────

	@Column(name = "referencia_pago", length = 100)
	private String referenciaPago;

	@Column(name = "motivo_fallo", length = 300)
	private String motivoFallo;

	@Column(name = "pago_por_verificar", nullable = false)
	private boolean pagoPorVerificar;

	/** Intentos de devolución hechos; cada uno usa su propia clave de idempotencia. */
	@Column(name = "intentos_devolucion", nullable = false)
	private int intentosDevolucion;

	// ─────────────────────────────────────────
	// Cancelación — MA-72
	// ─────────────────────────────────────────

	@Column(name = "motivo_cancelacion", length = 500)
	private String motivoCancelacion;

	@Column(name = "cancelada_por")
	private Long canceladaPor;

	@Column(name = "cancelada_en")
	private Instant canceladaEn;

	/** Lo que se devuelve al cancelar según la política (MA-73, MA-74). */
	@Column(name = "valor_devolucion", precision = 12, scale = 2)
	private BigDecimal valorDevolucion;

	/** La devolución se calculó pero la pasarela todavía no la aceptó: el conciliador la reintenta. */
	@Column(name = "devolucion_pendiente", nullable = false)
	private boolean devolucionPendiente;

	/** Hasta cuándo una ejecución tiene reservada la solicitud de la devolución a la pasarela. */
	@Column(name = "devolucion_reclamada_hasta")
	private Instant devolucionReclamadaHasta;

	@Column(name = "correlation_id", length = 100, updatable = false)
	private String correlationId;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	@Version
	private long version;

	protected Reserva() {
		// Requerido por JPA
	}

	/** Crea la reserva en PENDIENTE_PAGO con la copia de la cotización (MA-67, escenario 1). */
	public Reserva(Usuario huesped, Alojamiento alojamiento, RangoFechas rango, int huespedes, Cotizacion cotizacion,
			String correlationId, Instant ahora) {
		this.huesped = huesped;
		this.alojamiento = alojamiento;
		this.fechaLlegada = rango.llegada();
		this.fechaSalida = rango.salida();
		this.huespedes = huespedes;
		this.estado = EstadoReserva.PENDIENTE_PAGO;
		this.noches = Math.toIntExact(cotizacion.noches());
		this.precioNoche = cotizacion.precioNoche();
		this.subtotal = cotizacion.subtotal();
		this.totalDescuentos = cotizacion.totalDescuentos();
		this.limpieza = cotizacion.limpieza();
		this.totalServicios = cotizacion.totalServicios();
		this.porcentajeTarifaServicio = cotizacion.porcentajeTarifaServicio();
		this.tarifaServicio = cotizacion.tarifaServicio();
		this.total = cotizacion.total();
		cotizacion.servicios().forEach(linea -> servicios.add(new ReservaServicio(this, linea.servicioId(),
				linea.nombre(), linea.modalidad(), linea.costoUnitario(), Math.toIntExact(linea.cantidad()),
				linea.valor())));
		this.politicaCancelacionId = alojamiento.getPoliticaCancelacion().getId();
		this.politicaCancelacionCodigo = alojamiento.getPoliticaCancelacion().getCodigo();
		this.politicaCancelacion = alojamiento.getPoliticaCancelacion().getParametros();
		this.correlationId = correlationId;
		this.creadoEn = ahora;
		this.actualizadoEn = ahora;
	}

	// ─────────────────────────────────────────
	// Transiciones — MA-67, MA-68
	// ─────────────────────────────────────────

	/**
	 * Guarda la referencia del cobro preparado antes de cobrar (ADR-004): así el estado real
	 * siempre se puede consultar por referencia.
	 */
	public void registrarReferencia(String referenciaPago, Instant ahora) {
		if (estado == EstadoReserva.PENDIENTE_PAGO && this.referenciaPago == null) {
			this.referenciaPago = referenciaPago;
			this.actualizadoEn = ahora;
		}
	}

	/** Cuenta un nuevo intento de devolución y devuelve su número (1, 2, 3...). */
	public int siguienteIntentoDevolucion(Instant ahora) {
		this.actualizadoEn = ahora;
		return ++intentosDevolucion;
	}

	/** El pago se aprobó: PENDIENTE_PAGO → CONFIRMADA. */
	public void confirmar(String referenciaPago, Instant ahora) {
		cambiarEstado(EstadoReserva.CONFIRMADA, ahora);
		this.referenciaPago = referenciaPago;
	}

	/**
	 * El pago falló, no respondió o la retención venció: PENDIENTE_PAGO → PAGO_FALLIDO.
	 *
	 * @param porVerificar la pasarela tampoco respondió a la consulta: el conciliador revisará
	 *        después si hubo cobro para devolverlo
	 */
	public void marcarPagoFallido(String motivo, String referenciaPago, boolean porVerificar, Instant ahora) {
		cambiarEstado(EstadoReserva.PAGO_FALLIDO, ahora);
		this.motivoFallo = motivo;
		if (referenciaPago != null) {
			this.referenciaPago = referenciaPago;
		}
		this.pagoPorVerificar = porVerificar;
	}

	/** No se pudo pedir una devolución: el conciliador la reintentará. */
	public void marcarPagoPorVerificar(Instant ahora) {
		this.pagoPorVerificar = true;
		this.actualizadoEn = ahora;
	}

	/** El conciliador ya sabe si hubo cobro (y si lo hubo, pidió la devolución). */
	public void pagoVerificado(String referenciaPago, Instant ahora) {
		this.pagoPorVerificar = false;
		if (referenciaPago != null) {
			this.referenciaPago = referenciaPago;
		}
		this.actualizadoEn = ahora;
	}

	// ─────────────────────────────────────────
	// Transiciones — MA-71, MA-72
	// ─────────────────────────────────────────

	/**
	 * El huésped o el administrador cancela la reserva: CONFIRMADA o EN_REVISION → CANCELADA,
	 * siempre antes del día de llegada (MA-72). Una reserva en curso, completada o ya cancelada no
	 * se puede cancelar. Queda registrado quién, cuándo, por qué y cuánto se devuelve (MA-74).
	 *
	 * @param devolucion valor a devolver según la política, entre cero y el total
	 * @param hoy la fecha de hoy en la zona horaria del marketplace
	 */
	public void cancelar(Long usuarioId, String motivo, BigDecimal devolucion, LocalDate hoy, Instant ahora) {
		if (motivo == null || motivo.isBlank()) {
			throw new SolicitudInvalidaException("MOTIVO_REQUERIDO", "Debes indicar el motivo de la cancelación.");
		}
		if (estado != EstadoReserva.CONFIRMADA && estado != EstadoReserva.EN_REVISION) {
			throw new ReglaNegocioException("CANCELACION_NO_PERMITIDA",
					"Solo se puede cancelar una reserva confirmada o en revisión; esta está " + estado + ".");
		}
		if (!hoy.isBefore(fechaLlegada)) {
			throw new ReglaNegocioException("CANCELACION_NO_PERMITIDA",
					"La estadía ya empezó o terminó: la reserva no se puede cancelar.");
		}
		if (devolucion == null || devolucion.signum() < 0 || devolucion.compareTo(total) > 0) {
			throw new IllegalArgumentException("La devolución debe estar entre cero y el total de la reserva.");
		}
		cambiarEstado(EstadoReserva.CANCELADA, ahora);
		this.motivoCancelacion = motivo.strip();
		this.canceladaPor = usuarioId;
		this.canceladaEn = ahora;
		this.valorDevolucion = devolucion;
		this.devolucionPendiente = devolucion.signum() > 0 && referenciaPago != null;
	}

	/**
	 * Reserva para una sola ejecución la solicitud de la devolución pendiente durante
	 * {@code plazo}: mientras tanto, otra ejecución (por ejemplo el conciliador) no la pide a la
	 * vez. Si la ejecución se cae, el reclamo vence solo y la devolución se puede reintentar.
	 * <p>
	 * El intento (que forma la clave de idempotencia) solo cambia cuando la pasarela respondió con
	 * un error ({@link #devolucionFallida}). Así, si un reclamo vence mientras su petición sigue en
	 * curso, el reintento usa la misma clave y la pasarela no devuelve dos veces.
	 *
	 * @return el número del intento, o vacío si no hay devolución pendiente o ya otra la reclamó
	 */
	public OptionalInt reclamarDevolucion(Instant ahora, Duration plazo) {
		if (!devolucionPendiente || (devolucionReclamadaHasta != null && devolucionReclamadaHasta.isAfter(ahora))) {
			return OptionalInt.empty();
		}
		this.devolucionReclamadaHasta = ahora.plus(plazo);
		this.actualizadoEn = ahora;
		if (intentosDevolucion == 0) {
			intentosDevolucion = 1;
		}
		return OptionalInt.of(intentosDevolucion);
	}

	/**
	 * La pasarela no aceptó la devolución.
	 *
	 * @param puedeSeguirEnCurso no hubo respuesta: se conservan el reclamo hasta que venza y el
	 *        intento, para reintentar con la misma clave. Si la pasarela respondió con un error,
	 *        se suelta el reclamo y el siguiente intento usa una clave nueva.
	 */
	public void devolucionFallida(boolean puedeSeguirEnCurso, Instant ahora) {
		if (!puedeSeguirEnCurso) {
			intentosDevolucion++;
			devolucionReclamadaHasta = null;
		}
		this.actualizadoEn = ahora;
	}

	/** La pasarela aceptó la devolución de la cancelación (MA-73, escenario 2). */
	public void devolucionSolicitada(Instant ahora) {
		this.devolucionPendiente = false;
		this.devolucionReclamadaHasta = null;
		this.actualizadoEn = ahora;
	}

	/**
	 * Una reserva CONFIRMADA cuya fecha de salida ya llegó pasa a COMPLETADA (MA-71, escenario 3).
	 *
	 * @return si la completó; una reserva en otro estado o con la salida pendiente no cambia
	 */
	public boolean completar(LocalDate hoy, Instant ahora) {
		if (estado != EstadoReserva.CONFIRMADA || hoy.isBefore(fechaSalida)) {
			return false;
		}
		cambiarEstado(EstadoReserva.COMPLETADA, ahora);
		return true;
	}

	/**
	 * Por una moderación (alojamiento bloqueado o usuario restringido) la reserva confirmada pasa
	 * a EN_REVISION sin cancelarse (MA-80, MA-81).
	 */
	public void ponerEnRevision(Instant ahora) {
		cambiarEstado(EstadoReserva.EN_REVISION, ahora);
	}

	/** El administrador decide mantenerla: EN_REVISION → CONFIRMADA (MA-82). */
	public void mantener(Instant ahora) {
		cambiarEstado(EstadoReserva.CONFIRMADA, ahora);
	}

	/** El huésped que hizo la reserva. */
	public boolean esDe(Long usuarioId) {
		return huesped.getId().equals(usuarioId);
	}

	private void cambiarEstado(EstadoReserva destino, Instant ahora) {
		if (!estado.puedePasarA(destino)) {
			throw new ReglaNegocioException("TRANSICION_INVALIDA",
					"La reserva no puede pasar de " + estado + " a " + destino + ".");
		}
		this.estado = destino;
		this.actualizadoEn = ahora;
	}

	/** El huésped que reservó, el anfitrión del alojamiento o un administrador (ADR-009). */
	public boolean esVisiblePara(Usuario usuario) {
		return usuario.getRol() == Rol.ADMINISTRADOR
				|| esDe(usuario.getId())
				|| alojamiento.esDe(usuario.getId());
	}

	public RangoFechas rango() {
		return new RangoFechas(fechaLlegada, fechaSalida);
	}

	// ─────────────────────────────────────────
	// Getters
	// ─────────────────────────────────────────

	public Long getId() {
		return id;
	}

	public Usuario getHuesped() {
		return huesped;
	}

	public Alojamiento getAlojamiento() {
		return alojamiento;
	}

	public LocalDate getFechaLlegada() {
		return fechaLlegada;
	}

	public LocalDate getFechaSalida() {
		return fechaSalida;
	}

	public int getHuespedes() {
		return huespedes;
	}

	public EstadoReserva getEstado() {
		return estado;
	}

	public int getNoches() {
		return noches;
	}

	public BigDecimal getPrecioNoche() {
		return precioNoche;
	}

	public BigDecimal getSubtotal() {
		return subtotal;
	}

	public BigDecimal getTotalDescuentos() {
		return totalDescuentos;
	}

	public BigDecimal getLimpieza() {
		return limpieza;
	}

	public BigDecimal getTotalServicios() {
		return totalServicios;
	}

	public BigDecimal getPorcentajeTarifaServicio() {
		return porcentajeTarifaServicio;
	}

	public BigDecimal getTarifaServicio() {
		return tarifaServicio;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public List<ReservaServicio> getServicios() {
		return List.copyOf(servicios);
	}

	public String getPoliticaCancelacion() {
		return politicaCancelacion;
	}

	public String getReferenciaPago() {
		return referenciaPago;
	}

	public String getMotivoFallo() {
		return motivoFallo;
	}

	public int getIntentosDevolucion() {
		return intentosDevolucion;
	}

	public boolean isPagoPorVerificar() {
		return pagoPorVerificar;
	}

	public String getMotivoCancelacion() {
		return motivoCancelacion;
	}

	public Long getCanceladaPor() {
		return canceladaPor;
	}

	public Instant getCanceladaEn() {
		return canceladaEn;
	}

	public String getPoliticaCancelacionCodigo() {
		return politicaCancelacionCodigo;
	}

	public BigDecimal getValorDevolucion() {
		return valorDevolucion;
	}

	public boolean isDevolucionPendiente() {
		return devolucionPendiente;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	public Instant getActualizadoEn() {
		return actualizadoEn;
	}
}
