package co.edu.unbosque.marketplace.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import co.edu.unbosque.marketplace.model.Usuario;
import co.edu.unbosque.marketplace.model.Usuario.EstadoCuenta;
import co.edu.unbosque.marketplace.model.Usuario.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de usuario, igual que el UsuarioDTO de Software 1: se usa para registrarse, iniciar sesión
 * y responder.
 * <p>
 * Protecciones de Software 2 en la misma clase:
 * <ul>
 * <li>{@code id} y {@code estadoCuenta} son de solo lectura: si el cliente los envía se ignoran.</li>
 * <li>{@code password} es de solo escritura: nunca sale en una respuesta.</li>
 * <li>Las validaciones dependen del caso con los grupos {@link Registro} y {@link Login}.</li>
 * </ul>
 */
public record UsuarioDTO(

		@JsonProperty(access = Access.READ_ONLY)
		Long id,

		@NotBlank(groups = Registro.class, message = "El nombre es obligatorio.")
		@Size(groups = Registro.class, max = 100, message = "El nombre admite máximo 100 caracteres.")
		String nombre,

		@NotBlank(groups = { Registro.class, Login.class }, message = "El correo es obligatorio.")
		@Email(groups = Registro.class, message = "El correo no tiene un formato válido.")
		@Size(groups = Registro.class, max = 254, message = "El correo admite máximo 254 caracteres.")
		String email,

		@NotBlank(groups = Registro.class, message = "El teléfono es obligatorio.")
		@Pattern(groups = Registro.class, regexp = "^\\+?[0-9][0-9 ]{6,18}$",
				message = "El teléfono debe tener entre 7 y 20 dígitos.")
		String telefono,

		/* BCrypt solo usa los primeros 72 bytes: el servicio revisa además el tamaño en bytes. */
		@JsonProperty(access = Access.WRITE_ONLY)
		@NotBlank(groups = { Registro.class, Login.class }, message = "La contraseña es obligatoria.")
		@Size(groups = Registro.class, min = 8, max = 72,
				message = "La contraseña debe tener entre 8 y 72 caracteres.")
		String password,

		@NotNull(groups = Registro.class, message = "El rol es obligatorio: HUESPED o ANFITRION.")
		Rol rol,

		@JsonProperty(access = Access.READ_ONLY)
		EstadoCuenta estadoCuenta) {

	/** Validaciones del registro (MA-47). */
	public interface Registro {
	}

	/** Validaciones del inicio de sesión (MA-48): solo correo y contraseña. */
	public interface Login {
	}

	/** Convierte la entidad en DTO. Nunca copia la contraseña. */
	public static UsuarioDTO desde(Usuario usuario) {
		return new UsuarioDTO(usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getTelefono(),
				null, usuario.getRol(), usuario.getEstadoCuenta());
	}

	/** Evita que la contraseña aparezca si alguien registra el objeto en un log. */
	@Override
	public String toString() {
		return "UsuarioDTO[id=" + id + ", nombre=" + nombre + ", email=" + email + ", rol=" + rol + "]";
	}
}
