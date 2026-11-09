package co.edu.unbosque.marketplace.alojamientos.aplicacion;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import co.edu.unbosque.marketplace.alojamientos.dominio.Ciudad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTO de ciudad. El administrador la envía para agregarla al catálogo (MA-53, escenario 3). */
public record CiudadDTO(

		@JsonProperty(access = Access.READ_ONLY)
		Long id,

		@NotBlank(message = "El nombre es obligatorio.")
		@Size(max = 80, message = "El nombre admite máximo 80 caracteres.")
		String nombre,

		@NotBlank(message = "El departamento es obligatorio.")
		@Size(max = 80, message = "El departamento admite máximo 80 caracteres.")
		String departamento) {

	public static CiudadDTO desde(Ciudad ciudad) {
		return new CiudadDTO(ciudad.getId(), ciudad.getNombre(), ciudad.getDepartamento());
	}
}
