package co.edu.unbosque.marketplace.disponibilidad.dominio;

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
 * Periodo en que el anfitrión recibe huéspedes (MA-58). {@code fechaFin} es el último día de
 * salida posible: una estadía cabe si llega desde {@code fechaInicio} y sale a más tardar en
 * {@code fechaFin}.
 */
@Entity
@Table(name = "periodo_disponible")
public class PeriodoDisponible {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "alojamiento_id", nullable = false, updatable = false)
	private Alojamiento alojamiento;

	@Column(name = "fecha_inicio", nullable = false)
	private LocalDate fechaInicio;

	@Column(name = "fecha_fin", nullable = false)
	private LocalDate fechaFin;

	protected PeriodoDisponible() {
		// Requerido por JPA
	}

	public PeriodoDisponible(Alojamiento alojamiento, RangoFechas rango) {
		this.alojamiento = alojamiento;
		cambiarFechas(rango);
	}

	public void cambiarFechas(RangoFechas rango) {
		this.fechaInicio = rango.llegada();
		this.fechaFin = rango.salida();
	}

	public RangoFechas rango() {
		return new RangoFechas(fechaInicio, fechaFin);
	}

	public Long getId() {
		return id;
	}

	public Alojamiento getAlojamiento() {
		return alojamiento;
	}

	public LocalDate getFechaInicio() {
		return fechaInicio;
	}

	public LocalDate getFechaFin() {
		return fechaFin;
	}
}
