package co.edu.unbosque.marketplace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import co.edu.unbosque.marketplace.model.TipoAlojamiento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTO de tipo de alojamiento. El administrador lo envía para agregarlo al catálogo (MA-53, escenario 3). */
public record TipoAlojamientoDTO(

		@JsonProperty(access = Access.READ_ONLY)
		Short id,

		@NotBlank(message = "El código es obligatorio.")
		@Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$",
				message = "El código va en mayúsculas, sin espacios, por ejemplo GLAMPING.")
		String codigo,

		@NotBlank(message = "El nombre es obligatorio.")
		@Size(max = 80, message = "El nombre admite máximo 80 caracteres.")
		String nombre) {

	public static TipoAlojamientoDTO desde(TipoAlojamiento entidad) {
		return new TipoAlojamientoDTO(entidad.getId(), entidad.getCodigo(), entidad.getNombre());
	}
}
