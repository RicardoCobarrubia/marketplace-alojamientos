package co.edu.unbosque.marketplace.dto;

import co.edu.unbosque.marketplace.model.Usuario;
import co.edu.unbosque.marketplace.model.Usuario.EstadoCuenta;
import co.edu.unbosque.marketplace.model.Usuario.Rol;

/** Datos públicos de un usuario. Nunca incluye el hash de la contraseña. */
public record UsuarioResponse(
		Long id,
		String nombre,
		String email,
		String telefono,
		Rol rol,
		EstadoCuenta estadoCuenta) {

	public static UsuarioResponse desde(Usuario usuario) {
		return new UsuarioResponse(
				usuario.getId(),
				usuario.getNombre(),
				usuario.getEmail(),
				usuario.getTelefono(),
				usuario.getRol(),
				usuario.getEstadoCuenta());
	}
}
