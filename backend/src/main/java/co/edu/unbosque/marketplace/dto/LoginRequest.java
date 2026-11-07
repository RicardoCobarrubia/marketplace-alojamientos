package co.edu.unbosque.marketplace.dto;

import jakarta.validation.constraints.NotBlank;

/** Credenciales para iniciar sesión (MA-48). */
public record LoginRequest(

		@NotBlank(message = "El correo es obligatorio.")
		String email,

		@NotBlank(message = "La contraseña es obligatoria.")
		String password) {

	/** Evita que la contraseña aparezca si alguien registra el objeto en un log. */
	@Override
	public String toString() {
		return "LoginRequest[email=" + email + "]";
	}
}
