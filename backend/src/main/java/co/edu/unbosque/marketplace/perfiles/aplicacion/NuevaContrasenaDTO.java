package co.edu.unbosque.marketplace.perfiles.aplicacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** El código recibido y la nueva contraseña (MA-50, escenario 2). */
public record NuevaContrasenaDTO(

		@NotBlank(message = "El correo es obligatorio.")
		String email,

		@NotBlank(message = "El código es obligatorio.")
		@Pattern(regexp = "^\\d{6}$", message = "El código tiene 6 dígitos.")
		String codigo,

		/* Las mismas reglas que al registrarse; el servicio revisa además el tamaño en bytes. */
		@NotBlank(message = "La contraseña es obligatoria.")
		@Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
		String password) {

	/** Ni el código ni la contraseña aparecen si alguien registra el objeto en un log. */
	@Override
	public String toString() {
		return "NuevaContrasenaDTO[email=" + email + "]";
	}
}
