import type { Cotizacion } from '../types/reserva';
import { peticion } from './cliente';

/** Cotiza una estadía con su desglose (MA-63, MA-64). Pública para alojamientos activos. */
export function cotizar(alojamientoId: number, llegada: string, salida: string, servicioIds: number[]): Promise<Cotizacion> {
  const parametros = new URLSearchParams({ llegada, salida });
  if (servicioIds.length > 0) parametros.set('servicios', servicioIds.join(','));
  return peticion<Cotizacion>(`/api/alojamientos/${alojamientoId}/cotizacion?${parametros}`);
}
