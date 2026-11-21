/** Reglas de validación del lado del cliente. Replican las del backend (UsuarioDTO). */

export const FORMATO_CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Igual que el @Pattern del teléfono en UsuarioDTO: máximo 20 caracteres, al menos 7 dígitos,
 * un "+" opcional al inicio y como máximo un espacio entre dígitos.
 */
export const FORMATO_TELEFONO = /^(?=.{7,20}$)\+?\d(?: ?\d){6,19}$/;

export const MENSAJE_TELEFONO = 'El teléfono debe tener al menos 7 dígitos y máximo 20 caracteres.';

/** BCrypt solo usa los primeros 72 bytes de la contraseña, así que el backend los limita. */
export const MAXIMO_BYTES_CONTRASENA = 72;

export function bytesUtf8(texto: string): number {
  return new TextEncoder().encode(texto).length;
}
