import type { Ciudad, PoliticaCancelacion, Servicio, TipoAlojamiento } from '../types/alojamiento';
import { peticion } from './cliente';

/** Catálogos públicos para formularios y filtros (/api/catalogos). */

export function listarCiudades(): Promise<Ciudad[]> {
  return peticion<Ciudad[]>('/api/catalogos/ciudades', { autenticado: false });
}

export function listarTipos(): Promise<TipoAlojamiento[]> {
  return peticion<TipoAlojamiento[]>('/api/catalogos/tipos', { autenticado: false });
}

export function listarServicios(): Promise<Servicio[]> {
  return peticion<Servicio[]>('/api/catalogos/servicios', { autenticado: false });
}

export function listarPoliticasCancelacion(): Promise<PoliticaCancelacion[]> {
  return peticion<PoliticaCancelacion[]>('/api/catalogos/politicas-cancelacion', { autenticado: false });
}
