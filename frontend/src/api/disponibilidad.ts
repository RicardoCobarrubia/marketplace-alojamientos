import type { DatosPeriodo, Disponibilidad, PeriodoDisponible } from '../types/alojamiento';
import { peticion } from './cliente';

/** Disponibilidad de un alojamiento (MA-58, MA-59). */

const base = (alojamientoId: number) => `/api/alojamientos/${alojamientoId}`;

export function listarPeriodos(alojamientoId: number): Promise<PeriodoDisponible[]> {
  return peticion<PeriodoDisponible[]>(`${base(alojamientoId)}/periodos`);
}

export function crearPeriodo(alojamientoId: number, datos: DatosPeriodo): Promise<PeriodoDisponible> {
  return peticion<PeriodoDisponible>(`${base(alojamientoId)}/periodos`, { metodo: 'POST', cuerpo: datos });
}

export function editarPeriodo(
  alojamientoId: number,
  periodoId: number,
  datos: DatosPeriodo,
): Promise<PeriodoDisponible> {
  return peticion<PeriodoDisponible>(`${base(alojamientoId)}/periodos/${periodoId}`, {
    metodo: 'PUT',
    cuerpo: datos,
  });
}

export function eliminarPeriodo(alojamientoId: number, periodoId: number): Promise<void> {
  return peticion<void>(`${base(alojamientoId)}/periodos/${periodoId}`, { metodo: 'DELETE' });
}

/** Consulta si se puede reservar entre `llegada` y `salida` (fechas ISO). */
export function consultarDisponibilidad(
  alojamientoId: number,
  llegada: string,
  salida: string,
): Promise<Disponibilidad> {
  const parametros = new URLSearchParams({ llegada, salida });
  return peticion<Disponibilidad>(`${base(alojamientoId)}/disponibilidad?${parametros}`);
}
