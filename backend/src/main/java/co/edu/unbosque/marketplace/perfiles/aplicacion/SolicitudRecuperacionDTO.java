package co.edu.unbosque.marketplace.perfiles.aplicacion;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** El correo al que se envía el código para recuperar la contraseña (MA-50, escenario 1). */
public record SolicitudRecuperacionDTO(

		@NotBlank(message = "El correo es obligatorio.")
		@Email(message = "El correo no tiene un formato válido.")
		String email) {
}
