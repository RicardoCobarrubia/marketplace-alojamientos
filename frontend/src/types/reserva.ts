import type { Modalidad } from './alojamiento';

/** Tipos que reflejan ReservaDTO del backend (MA-66 a MA-74). */

export type EstadoReserva = 'PENDIENTE_PAGO' | 'CONFIRMADA' | 'PAGO_FALLIDO' | 'EN_REVISION' | 'CANCELADA' | 'COMPLETADA';

export interface ServicioReservado {
  servicioId: number;
  nombre: string;
  modalidad: Modalidad;
  costoUnitario: number;
  cantidad: number;
  valor: number;
}

export interface Reserva {
  id: number;
  alojamientoId: number;
  llegada: string;
  salida: string;
  huespedes: number;
  servicioIds: number[];
  estado: EstadoReserva;
  huespedId: number;
  noches: number;
  precioNoche: number;
  subtotal: number;
  totalDescuentos: number;
  limpieza: number;
  servicios: ServicioReservado[];
  totalServicios: number;
  tarifaServicio: number;
  total: number;
  motivoFallo: string | null;
  creadoEn: string;
  motivoCancelacion: string | null;
  canceladaEn: string | null;
  valorDevolucion: number | null;
  devolucionPendiente: boolean | null;
}

export const NOMBRE_ESTADO_RESERVA: Record<EstadoReserva, string> = {
  PENDIENTE_PAGO: 'Pendiente de pago',
  CONFIRMADA: 'Confirmada',
  PAGO_FALLIDO: 'Pago fallido',
  EN_REVISION: 'En revisión',
  CANCELADA: 'Cancelada',
  COMPLETADA: 'Completada',
};
