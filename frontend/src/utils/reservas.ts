import type { Reserva } from '../types/reserva';
import { DIAS_PARA_RESENAR } from '../types/resena';
import { aIso, desdeIso, hoyIso } from './formato';

/**
 * Las próximas estadías primero (por fecha de llegada) y después las que ya pasaron, de la más
 * reciente a la más antigua.
 */
export function ordenarReservas(reservas: Reserva[]): Reserva[] {
  const hoy = hoyIso();
  const proximas = reservas.filter((r) => r.salida >= hoy).sort((a, b) => a.llegada.localeCompare(b.llegada));
  const pasadas = reservas.filter((r) => r.salida < hoy).sort((a, b) => b.llegada.localeCompare(a.llegada));
  return [...proximas, ...pasadas];
}

/** Mismas reglas que Reserva.cancelar del backend (MA-72): confirmada o en revisión y antes de la llegada. */
export function sePuedeCancelar(r: Reserva): boolean {
  return (r.estado === 'CONFIRMADA' || r.estado === 'EN_REVISION') && hoyIso() < r.llegada;
}

/** Último día para reseñar: 30 días después de la salida (MA-77). */
export function limiteParaResenar(r: Reserva): string {
  const limite = desdeIso(r.salida);
  limite.setDate(limite.getDate() + DIAS_PARA_RESENAR);
  return aIso(limite);
}

/** La estadía terminó y sigue dentro del plazo. Si ya tiene reseña, el backend responde RESENA_DUPLICADA. */
export function sePuedeResenar(r: Reserva): boolean {
  return r.estado === 'COMPLETADA' && hoyIso() <= limiteParaResenar(r);
}
