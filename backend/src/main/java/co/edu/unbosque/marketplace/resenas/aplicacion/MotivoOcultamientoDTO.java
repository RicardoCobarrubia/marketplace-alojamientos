package co.edu.unbosque.marketplace.resenas.aplicacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Por qué el administrador oculta una reseña (MA-79). */
public record MotivoOcultamientoDTO(

		@NotBlank(message = "Debes indicar el motivo.")
		@Size(max = 500, message = "El motivo no puede superar 500 caracteres.")
		String motivo) {
}
