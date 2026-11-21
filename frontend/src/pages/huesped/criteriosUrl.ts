import type { CriteriosBusqueda } from '../../types/busqueda';

/** Los filtros viven en la URL (?ciudadId=2&llegada=...) para poder volver atrás o compartir la búsqueda. */

function numero(valor: string | null): number | undefined {
  if (valor === null || valor.trim() === '') return undefined;
  const n = Number(valor);
  return Number.isFinite(n) ? n : undefined;
}

export function leerCriterios(parametros: URLSearchParams): CriteriosBusqueda {
  const servicioIds = parametros
    .getAll('servicioIds')
    .map(Number)
    .filter((n) => Number.isInteger(n) && n > 0);
  return {
    ciudadId: numero(parametros.get('ciudadId')),
    llegada: parametros.get('llegada') ?? undefined,
    salida: parametros.get('salida') ?? undefined,
    huespedes: numero(parametros.get('huespedes')),
    tipoId: numero(parametros.get('tipoId')),
    precioMin: numero(parametros.get('precioMin')),
    precioMax: numero(parametros.get('precioMax')),
    servicioIds: servicioIds.length > 0 ? servicioIds : undefined,
    pagina: numero(parametros.get('pagina')),
  };
}

export function escribirCriterios(criterios: CriteriosBusqueda): URLSearchParams {
  const parametros = new URLSearchParams();
  for (const [clave, valor] of Object.entries(criterios)) {
    if (valor === undefined || valor === '' || (clave === 'pagina' && valor === 0)) continue;
    if (Array.isArray(valor)) valor.forEach((v) => parametros.append(clave, String(v)));
    else parametros.set(clave, String(valor));
  }
  return parametros;
}

/** Cuántos filtros de "Más filtros" hay puestos, para mostrarlo en el botón. */
export function contarFiltrosExtra(c: CriteriosBusqueda): number {
  return [c.tipoId, c.precioMin, c.precioMax].filter((v) => v !== undefined).length + (c.servicioIds?.length ?? 0);
}
