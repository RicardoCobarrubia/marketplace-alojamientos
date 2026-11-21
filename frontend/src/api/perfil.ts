import type { Usuario } from '../types/auth';
import { peticion } from './cliente';

/** Perfil del usuario con sesión (MA-51, MA-52, RF-05, RF-06). */

export function obtenerMiPerfil(): Promise<Usuario> {
  return peticion<Usuario>('/api/usuarios/me');
}

/**
 * Solo se envían nombre y teléfono: el backend rechaza con CAMPO_NO_EDITABLE si llegan el correo
 * o el rol.
 */
export function actualizarMiPerfil(datos: { nombre: string; telefono: string }): Promise<Usuario> {
  return peticion<Usuario>('/api/usuarios/me', { metodo: 'PUT', cuerpo: datos });
}

/**
 * Desactiva la cuenta: cierra sus sesiones y pausa sus alojamientos activos. Falla con
 * RESERVAS_VIGENTES si tiene reservas confirmadas que aún no terminan.
 */
export function desactivarMiCuenta(): Promise<void> {
  return peticion<void>('/api/usuarios/me/desactivacion', { metodo: 'POST' });
}
