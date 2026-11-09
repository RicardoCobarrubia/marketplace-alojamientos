import type {
  Alojamiento,
  DatosAlojamiento,
  DatosServicioAdicional,
  ServicioAdicional,
} from '../types/alojamiento';
import { peticion } from './cliente';

/** Alojamientos del anfitrión (MA-53 a MA-56). */

const BASE = '/api/alojamientos';

export function listarMisAlojamientos(): Promise<Alojamiento[]> {
  return peticion<Alojamiento[]>(`${BASE}/mios`);
}

export function obtenerAlojamiento(id: number): Promise<Alojamiento> {
  return peticion<Alojamiento>(`${BASE}/${id}`);
}

/** Crea el alojamiento en BORRADOR. */
export function crearAlojamiento(datos: DatosAlojamiento): Promise<Alojamiento> {
  return peticion<Alojamiento>(BASE, { metodo: 'POST', cuerpo: datos });
}

export function editarAlojamiento(id: number, datos: DatosAlojamiento): Promise<Alojamiento> {
  return peticion<Alojamiento>(`${BASE}/${id}`, { metodo: 'PUT', cuerpo: datos });
}

/** BORRADOR → PENDIENTE_REVISION. */
export function enviarARevision(id: number): Promise<Alojamiento> {
  return peticion<Alojamiento>(`${BASE}/${id}/enviar-revision`, { metodo: 'POST' });
}

/** ACTIVA → PAUSADA. */
export function pausarAlojamiento(id: number): Promise<Alojamiento> {
  return peticion<Alojamiento>(`${BASE}/${id}/pausar`, { metodo: 'POST' });
}

/** PAUSADA → ACTIVA. */
export function reanudarAlojamiento(id: number): Promise<Alojamiento> {
  return peticion<Alojamiento>(`${BASE}/${id}/reanudar`, { metodo: 'POST' });
}

export function agregarServicioAdicional(id: number, datos: DatosServicioAdicional): Promise<ServicioAdicional> {
  return peticion<ServicioAdicional>(`${BASE}/${id}/servicios-adicionales`, { metodo: 'POST', cuerpo: datos });
}

export function editarServicioAdicional(
  id: number,
  servicioId: number,
  datos: DatosServicioAdicional,
): Promise<ServicioAdicional> {
  return peticion<ServicioAdicional>(`${BASE}/${id}/servicios-adicionales/${servicioId}`, {
    metodo: 'PUT',
    cuerpo: datos,
  });
}

/** Lo retira de la oferta; el backend lo conserva para las reservas que ya lo contrataron. */
export function retirarServicioAdicional(id: number, servicioId: number): Promise<void> {
  return peticion<void>(`${BASE}/${id}/servicios-adicionales/${servicioId}`, { metodo: 'DELETE' });
}
