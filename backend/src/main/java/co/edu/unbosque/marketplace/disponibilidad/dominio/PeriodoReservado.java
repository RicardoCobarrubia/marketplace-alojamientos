package co.edu.unbosque.marketplace.disponibilidad.dominio;

import java.time.Instant;
import java.time.LocalDate;

import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Fechas tomadas por una reserva. Mientras se paga quedan retenidas hasta
 * {@code retenidoHasta} (10 minutos, MA-60); al confirmarse la retención pasa a definitiva
 * ({@code retenidoHasta} vacío). Al cancelar, fallar el pago o vencer la retención se libera.
 * <p>
 * La restricción de exclusión de la base de datos impide que existan dos rangos activos que se
 * crucen en el mismo alojamiento (ADR-003). Las reservas las crea MA-66.
 */
@Entity
@Table(name = "periodo_reservado")
public class PeriodoReservado {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "alojamiento_id", nullable = false, updatable = false)
	private Alojamiento alojamiento;

	@Column(name = "reserva_id", unique = true)
	private Long reservaId;

	@Column(name = "fecha_llegada", nullable = false)
	private LocalDate fechaLlegada;

	@Column(name = "fecha_salida", nullable = false)
	private LocalDate fechaSalida;

	@Column(name = "retenido_hasta")
	private Instant retenidoHasta;

	@Column(name = "liberado_en")
	private Instant liberadoEn;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	protected PeriodoReservado() {
		// Requerido por JPA
	}

	/** Retiene las fechas hasta {@code retenidoHasta} mientras se procesa el pago. */
	public PeriodoReservado(Alojamiento alojamiento, Long reservaId, RangoFechas rango, Instant retenidoHasta,
			Instant ahora) {
		this.alojamiento = alojamiento;
		this.reservaId = reservaId;
		this.fechaLlegada = rango.llegada();
		this.fechaSalida = rango.salida();
		this.retenidoHasta = retenidoHasta;
		this.creadoEn = ahora;
	}

	/** Sigue ocupando las fechas: no se liberó y, si es una retención, no ha vencido. */
	public boolean estaVigente(Instant ahora) {
		return liberadoEn == null && (retenidoHasta == null || retenidoHasta.isAfter(ahora));
	}

	/** La reserva se confirmó: el bloqueo pasa a ser definitivo. */
	public void bloquearDefinitivamente() {
		this.retenidoHasta = null;
	}

	public void liberar(Instant ahora) {
		this.liberadoEn = ahora;
	}

	public RangoFechas rango() {
		return new RangoFechas(fechaLlegada, fechaSalida);
	}

	public Long getId() {
		return id;
	}

	public Long getReservaId() {
		return reservaId;
	}

	public LocalDate getFechaLlegada() {
		return fechaLlegada;
	}

	public LocalDate getFechaSalida() {
		return fechaSalida;
	}

	public Instant getRetenidoHasta() {
		return retenidoHasta;
	}

	public Instant getLiberadoEn() {
		return liberadoEn;
	}
}
