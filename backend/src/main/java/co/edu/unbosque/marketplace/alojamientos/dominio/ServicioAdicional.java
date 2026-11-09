package co.edu.unbosque.marketplace.alojamientos.dominio;

import java.math.BigDecimal;

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
import jakarta.persistence.Table;

/**
 * Servicio opcional que el anfitrión ofrece con costo, por ejemplo desayuno o traslado (MA-55).
 * Al retirarlo se marca inactivo en lugar de borrarlo, para no perder el que ya contrató una reserva.
 */
@Entity
@Table(name = "servicio_adicional")
public class ServicioAdicional {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "alojamiento_id", nullable = false)
	private Alojamiento alojamiento;

	@Column(nullable = false, length = 100)
	private String nombre;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal costo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Modalidad modalidad;

	@Column(nullable = false)
	private boolean activo;

	/** Cómo se cobra el servicio en la cotización. */
	public enum Modalidad {
		POR_ESTADIA,
		POR_NOCHE
	}

	protected ServicioAdicional() {
		// Requerido por JPA
	}

	ServicioAdicional(Alojamiento alojamiento, String nombre, BigDecimal costo, Modalidad modalidad) {
		this.alojamiento = alojamiento;
		this.activo = true;
		actualizar(nombre, costo, modalidad);
	}

	public void actualizar(String nombre, BigDecimal costo, Modalidad modalidad) {
		this.nombre = nombre.trim();
		this.costo = costo;
		this.modalidad = modalidad;
	}

	public void retirar() {
		this.activo = false;
	}

	public Long getId() {
		return id;
	}

	public Alojamiento getAlojamiento() {
		return alojamiento;
	}

	public String getNombre() {
		return nombre;
	}

	public BigDecimal getCosto() {
		return costo;
	}

	public Modalidad getModalidad() {
		return modalidad;
	}

	public boolean isActivo() {
		return activo;
	}
}
