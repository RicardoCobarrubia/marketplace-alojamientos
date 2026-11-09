package co.edu.unbosque.marketplace.alojamientos.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Servicio incluido en un alojamiento (wifi, cocina, piscina...). Catálogo que el administrador amplía sin cambiar código (MA-53, escenario 3). */
@Entity
@Table(name = "servicio")
public class Servicio {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Short id;

	@Column(nullable = false, unique = true, length = 40)
	private String codigo;

	@Column(nullable = false, length = 80)
	private String nombre;

	protected Servicio() {
		// Requerido por JPA
	}

	public Servicio(String codigo, String nombre) {
		this.codigo = codigo.trim();
		this.nombre = nombre.trim();
	}

	public Short getId() {
		return id;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getNombre() {
		return nombre;
	}
}
