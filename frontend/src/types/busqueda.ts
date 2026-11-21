import type { Alojamiento } from './alojamiento';

/** Filtros de GET /api/alojamientos (MA-61). Todos opcionales; las fechas van juntas. */
export interface CriteriosBusqueda {
  ciudadId?: number;
  llegada?: string;
  salida?: string;
  huespedes?: number;
  tipoId?: number;
  precioMin?: number;
  precioMax?: number;
  servicioIds?: number[];
  /** Empieza en 0. */
  pagina?: number;
  /** De 1 a 50; el backend usa 20 si no llega. */
  tamano?: number;
}

/** Una página de resultados, del más barato por noche al más caro (ResultadoBusquedaDTO). */
export interface ResultadoBusqueda {
  alojamientos: Alojamiento[];
  pagina: number;
  tamano: number;
  total: number;
  totalPaginas: number;
  /** Texto para mostrar cuando no hay resultados; null si los hay. */
  mensaje: string | null;
}
