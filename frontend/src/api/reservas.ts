import type { DatosReserva, EstadoReserva, Reserva, ReservaEnProceso } from '../types/reserva';
import { peticion } from './cliente';

/**
 * Reservas (MA-66 a MA-74). El backend filtra por rol: el huésped ve las suyas, el anfitrión las
 * de sus alojamientos y el administrador todas.
 */

export function listarReservas(estado?: EstadoReserva): Promise<Reserva[]> {
  const consulta = estado ? `?${new URLSearchParams({ estado })}` : '';
  return peticion<Reserva[]>(`/api/reservas${consulta}`);
}

export function obtenerReserva(id: number): Promise<Reserva> {
  return peticion<Reserva>(`/api/reservas/${id}`);
}

/**
 * Reserva y paga (MA-66, MA-67). `clave` es la Idempotency-Key del intento: si se repite la misma
 * solicitud con la misma clave, el backend no cobra dos veces y responde lo mismo.
 * Devuelve la reserva confirmada (201) o el aviso de que sigue procesándose (202). Un pago
 * rechazado (402), sin respuesta de la pasarela (504) o fechas ocupadas (409) llegan como ApiError.
 */
export function crearReserva(datos: DatosReserva, clave: string): Promise<Reserva | ReservaEnProceso> {
  return peticion<Reserva | ReservaEnProceso>('/api/reservas', {
    metodo: 'POST',
    cuerpo: datos,
    cabeceras: { 'Idempotency-Key': clave },
  });
}

export function estaEnProceso(respuesta: Reserva | ReservaEnProceso): respuesta is ReservaEnProceso {
  return 'reservaId' in respuesta && !('id' in respuesta);
}

/** Cancela una reserva confirmada o en revisión (MA-72); el backend calcula la devolución (MA-73). */
export function cancelarReserva(id: number, motivo: string): Promise<Reserva> {
  return peticion<Reserva>(`/api/reservas/${id}/cancelacion`, { metodo: 'POST', cuerpo: { motivo } });
}
