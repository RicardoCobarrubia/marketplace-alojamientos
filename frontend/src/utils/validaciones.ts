/** Reglas de validación del lado del cliente. Replican las del backend (UsuarioDTO). */

export const FORMATO_CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Igual que el @Pattern del teléfono en UsuarioDTO. */
export const FORMATO_TELEFONO = /^\+?[0-9][0-9 ]{6,18}$/;

/** BCrypt solo usa los primeros 72 bytes de la contraseña, así que el backend los limita. */
export const MAXIMO_BYTES_CONTRASENA = 72;

export function bytesUtf8(texto: string): number {
  return new TextEncoder().encode(texto).length;
}
