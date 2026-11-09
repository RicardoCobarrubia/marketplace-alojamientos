package co.edu.unbosque.marketplace.alojamientos.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Regla de la casa, por ejemplo "No se permiten fiestas". */
@Entity
@Table(name = "regla_alojamiento")
public class ReglaAlojamiento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "alojamiento_id", nullable = false)
	private Alojamiento alojamiento;

	@Column(nullable = false, length = 300)
	private String descripcion;

	@Column(nullable = false)
	private short orden;

	protected ReglaAlojamiento() {
		// Requerido por JPA
	}

	ReglaAlojamiento(Alojamiento alojamiento, String descripcion, short orden) {
		this.alojamiento = alojamiento;
		this.descripcion = descripcion.trim();
		this.orden = orden;
	}

	public Long getId() {
		return id;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public short getOrden() {
		return orden;
	}
}
