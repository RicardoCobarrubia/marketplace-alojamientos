import type { Resena } from '../types/admin';
import type { ResenasAlojamiento } from '../types/resena';
import { peticion } from './cliente';

/** Reseñas de los huéspedes (MA-77, MA-78). */

/** Publica la reseña de una estadía completada, dentro de los 30 días siguientes a la salida. */
export function publicarResena(reservaId: number, calificacion: number, comentario: string): Promise<Resena> {
  return peticion<Resena>(`/api/reservas/${reservaId}/resena`, { metodo: 'POST', cuerpo: { calificacion, comentario } });
}

export function listarResenasAlojamiento(alojamientoId: number): Promise<ResenasAlojamiento> {
  return peticion<ResenasAlojamiento>(`/api/alojamientos/${alojamientoId}/resenas`);
}
