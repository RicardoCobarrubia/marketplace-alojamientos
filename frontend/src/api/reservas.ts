import type { EstadoReserva, Reserva } from '../types/reserva';
import { peticion } from './cliente';

/** Reservas (MA-66 a MA-72). El backend filtra por rol: el huésped ve las suyas, el anfitrión las de sus alojamientos. */

export function listarReservas(estado?: EstadoReserva): Promise<Reserva[]> {
  const consulta = estado ? `?${new URLSearchParams({ estado })}` : '';
  return peticion<Reserva[]>(`/api/reservas${consulta}`);
}

/** La ven el huésped, el anfitrión del alojamiento o un administrador. */
export function obtenerReserva(id: number): Promise<Reserva> {
  return peticion<Reserva>(`/api/reservas/${id}`);
}

/** Cancela una reserva CONFIRMADA o EN_REVISION antes de la llegada (huésped). */
export function cancelarReserva(id: number, motivo: string): Promise<Reserva> {
  return peticion<Reserva>(`/api/reservas/${id}/cancelacion`, { metodo: 'POST', cuerpo: { motivo } });
}
