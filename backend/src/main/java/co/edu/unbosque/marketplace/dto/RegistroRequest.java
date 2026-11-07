package co.edu.unbosque.marketplace.dto;

import co.edu.unbosque.marketplace.model.Usuario.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos para registrarse como huésped o anfitrión (MA-47). */
public record RegistroRequest(

		@NotBlank(message = "El nombre es obligatorio.")
		@Size(max = 100, message = "El nombre admite máximo 100 caracteres.")
		String nombre,

		@NotBlank(message = "El correo es obligatorio.")
		@Email(message = "El correo no tiene un formato válido.")
		@Size(max = 254, message = "El correo admite máximo 254 caracteres.")
		String email,

		@NotBlank(message = "El teléfono es obligatorio.")
		@Pattern(regexp = "^\\+?[0-9 ]{7,20}$", message = "El teléfono debe tener entre 7 y 20 dígitos.")
		String telefono,

		/* BCrypt solo usa los primeros 72 bytes, por eso el máximo. */
		@NotBlank(message = "La contraseña es obligatoria.")
		@Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
		String password,

		@NotNull(message = "El rol es obligatorio: HUESPED o ANFITRION.")
		Rol rol) {

	/** Evita que la contraseña aparezca si alguien registra el objeto en un log. */
	@Override
	public String toString() {
		return "RegistroRequest[nombre=" + nombre + ", email=" + email + ", rol=" + rol + "]";
	}
}
