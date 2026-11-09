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

/** Ruta o referencia de una imagen del alojamiento. La primera es la principal. */
@Entity
@Table(name = "imagen_alojamiento")
public class ImagenAlojamiento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "alojamiento_id", nullable = false)
	private Alojamiento alojamiento;

	@Column(nullable = false, length = 500)
	private String ruta;

	@Column(name = "es_principal", nullable = false)
	private boolean esPrincipal;

	@Column(nullable = false)
	private short orden;

	protected ImagenAlojamiento() {
		// Requerido por JPA
	}

	ImagenAlojamiento(Alojamiento alojamiento, String ruta, boolean esPrincipal, short orden) {
		this.alojamiento = alojamiento;
		this.ruta = ruta.trim();
		this.esPrincipal = esPrincipal;
		this.orden = orden;
	}

	public Long getId() {
		return id;
	}

	public String getRuta() {
		return ruta;
	}

	public boolean isEsPrincipal() {
		return esPrincipal;
	}

	public short getOrden() {
		return orden;
	}
}
