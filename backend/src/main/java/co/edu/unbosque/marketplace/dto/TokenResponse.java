package co.edu.unbosque.marketplace.dto;

import java.time.Instant;

/**
 * Respuesta del inicio de sesión. El frontend manda {@code token} en la cabecera
 * {@code Authorization: Bearer <token>} y usa {@code usuario.rol} para mostrar la vista de su rol.
 */
public record TokenResponse(
		String token,
		String tipo,
		Instant expiraEn,
		UsuarioResponse usuario) {

	public static TokenResponse bearer(String token, Instant expiraEn, UsuarioResponse usuario) {
		return new TokenResponse(token, "Bearer", expiraEn, usuario);
	}
}
