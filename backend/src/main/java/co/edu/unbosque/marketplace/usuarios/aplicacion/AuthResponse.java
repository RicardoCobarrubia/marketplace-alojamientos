package co.edu.unbosque.marketplace.usuarios.aplicacion;

import java.time.Instant;

/**
 * Respuesta del login, como la clase interna AuthResponse de Software 1. El frontend manda
 * {@code token} en la cabecera {@code Authorization: Bearer <token>} y usa {@code usuario.rol}
 * para mostrar la vista de su rol.
 */
public record AuthResponse(String token, String tipo, Instant expiraEn, UsuarioDTO usuario) {
}
