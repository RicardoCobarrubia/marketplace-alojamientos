import type { Notificacion } from '../types/notificacion';
import { peticion } from './cliente';

/** Notificaciones internas del usuario con sesión (MA-75, MA-76, RF-31, RF-32). */

/** Las más recientes primero. */
export function listarNotificaciones(): Promise<Notificacion[]> {
  return peticion<Notificacion[]>('/api/notificaciones');
}

export function marcarNotificacionLeida(id: number): Promise<Notificacion> {
  return peticion<Notificacion>(`/api/notificaciones/${id}/lectura`, { metodo: 'POST' });
}
