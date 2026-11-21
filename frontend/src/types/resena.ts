import type { Resena } from './admin';

/** Respuesta de GET /api/alojamientos/{id}/resenas (MA-78): reseñas visibles y su promedio. */
export interface ResenasAlojamiento {
  alojamientoId: number;
  /** De 1.0 a 5.0 con un decimal; null si no hay reseñas visibles. */
  promedio: number | null;
  cantidad: number;
  resenas: Resena[];
}

/** Límites de ResenaDTO y Resena en el backend (MA-77). */
export const LARGO_MINIMO_COMENTARIO = 10;
export const LARGO_MAXIMO_COMENTARIO = 1000;
/** Días después de la salida en los que el huésped puede reseñar. */
export const DIAS_PARA_RESENAR = 30;
