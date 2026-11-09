package co.edu.unbosque.marketplace.alojamientos.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Ciudad donde está un alojamiento. Catálogo que el administrador amplía sin cambiar código. */
@Entity
@Table(name = "ciudad")
public class Ciudad {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 80)
	private String nombre;

	@Column(nullable = false, length = 80)
	private String departamento;

	protected Ciudad() {
		// Requerido por JPA
	}

	public Ciudad(String nombre, String departamento) {
		this.nombre = nombre.trim();
		this.departamento = departamento.trim();
	}

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public String getDepartamento() {
		return departamento;
	}
}
