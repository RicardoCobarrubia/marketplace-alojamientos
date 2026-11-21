import type { CriteriosBusqueda, ResultadoBusqueda } from '../types/busqueda';
import { peticion } from './cliente';

/**
 * Busca alojamientos publicados (MA-61). Es pública. Los servicios van repetidos en la URL
 * (?servicioIds=1&servicioIds=4) y el alojamiento debe tenerlos todos.
 */
export function buscarAlojamientos(criterios: CriteriosBusqueda): Promise<ResultadoBusqueda> {
  const parametros = new URLSearchParams();
  for (const [clave, valor] of Object.entries(criterios)) {
    if (valor === undefined || valor === '') continue;
    if (Array.isArray(valor)) valor.forEach((v) => parametros.append(clave, String(v)));
    else parametros.set(clave, String(valor));
  }
  const consulta = parametros.toString();
  return peticion<ResultadoBusqueda>(`/api/alojamientos${consulta ? `?${consulta}` : ''}`);
}
