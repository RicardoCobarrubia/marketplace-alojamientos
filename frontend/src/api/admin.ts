import type { Alojamiento, Ciudad, EstadoAlojamiento, Servicio, TipoAlojamiento } from '../types/alojamiento';
import { peticion } from './cliente';

/** Endpoints del administrador (/api/admin/**, MA-57 y MA-53 escenario 3). */

export function listarAlojamientosAdmin(estado?: EstadoAlojamiento): Promise<Alojamiento[]> {
  const consulta = estado ? `?${new URLSearchParams({ estado })}` : '';
  return peticion<Alojamiento[]>(`/api/admin/alojamientos${consulta}`);
}

/** PENDIENTE_REVISION → ACTIVA. */
export function aprobarAlojamiento(id: number): Promise<Alojamiento> {
  return peticion<Alojamiento>(`/api/admin/alojamientos/${id}/aprobar`, { metodo: 'POST' });
}

/** PENDIENTE_REVISION → BORRADOR, con el motivo que verá el anfitrión. */
export function rechazarAlojamiento(id: number, motivoRechazo: string): Promise<Alojamiento> {
  return peticion<Alojamiento>(`/api/admin/alojamientos/${id}/rechazar`, {
    metodo: 'POST',
    cuerpo: { motivoRechazo },
  });
}

export function crearCiudad(datos: { nombre: string; departamento: string }): Promise<Ciudad> {
  return peticion<Ciudad>('/api/admin/catalogos/ciudades', { metodo: 'POST', cuerpo: datos });
}

export function crearTipo(datos: { codigo: string; nombre: string }): Promise<TipoAlojamiento> {
  return peticion<TipoAlojamiento>('/api/admin/catalogos/tipos', { metodo: 'POST', cuerpo: datos });
}

export function crearServicio(datos: { codigo: string; nombre: string }): Promise<Servicio> {
  return peticion<Servicio>('/api/admin/catalogos/servicios', { metodo: 'POST', cuerpo: datos });
}
