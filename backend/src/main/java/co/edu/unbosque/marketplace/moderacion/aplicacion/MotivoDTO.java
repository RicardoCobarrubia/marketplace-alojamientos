package co.edu.unbosque.marketplace.moderacion.aplicacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo obligatorio de una acción de moderación. */
public record MotivoDTO(

		@NotBlank(message = "Debes indicar el motivo.")
		@Size(max = 500, message = "El motivo no puede superar 500 caracteres.")
		String motivo) {
}
