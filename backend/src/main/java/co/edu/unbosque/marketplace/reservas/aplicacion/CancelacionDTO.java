package co.edu.unbosque.marketplace.reservas.aplicacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Solicitud de cancelación del huésped (MA-72). */
public record CancelacionDTO(

		@NotBlank(message = "Debes indicar el motivo de la cancelación.")
		@Size(max = 500, message = "El motivo no puede superar 500 caracteres.")
		String motivo) {
}
