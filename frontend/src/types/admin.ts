/** Tipos de los endpoints del administrador: panel, moderación, reseñas, auditoría y tarifas. */

/** GET /api/admin/panel/indicadores (MA-83). */
export interface Indicadores {
  usuarios: number;
  usuariosPorRol: Record<string, number>;
  alojamientosActivos: number;
  reservas: number;
  reservasPorEstado: Record<string, number>;
  generadoEn: string;
}

export interface CiudadReservas {
  ciudadId: number;
  ciudad: string;
  departamento: string;
  reservas: number;
}

/** GET /api/admin/panel/ingresos (MA-84). */
export interface Ingresos {
  desde: string;
  hasta: string;
  ingresosEstimados: number;
  reservas: number;
  ciudadesConMasReservas: CiudadReservas[];
}

/** Respuesta de bloquear, desbloquear, restringir y levantar restricción (MA-80, MA-81). */
export interface ResultadoModeracion {
  entidad: string;
  id: number;
  estado: string;
  motivo: string | null;
  reservasEnRevision: number;
  alojamientosPausados: number;
}

/** ResenaDTO (MA-77 a MA-79). */
export interface Resena {
  id: number;
  reservaId: number | null;
  alojamientoId: number | null;
  huespedId: number | null;
  calificacion: number;
  comentario: string;
  creadaEn: string;
  oculta: boolean | null;
  motivoOcultamiento: string | null;
}

/** RegistroAuditoriaDTO (MA-87). */
export interface RegistroAuditoria {
  id: number;
  ocurridoEn: string;
  actorId: number | null;
  actorRol: string | null;
  accion: string;
  entidad: string | null;
  entidadId: number | null;
  estadoAnterior: string | null;
  estadoNuevo: string | null;
  resultado: string;
  detalle: string | null;
  correlationId: string | null;
}

/** Filtro de auditoría: el backend acepta uno solo a la vez. */
export type FiltroAuditoria =
  | { tipo: 'ninguno' }
  | { tipo: 'entidad'; entidad: string; entidadId: number }
  | { tipo: 'actor'; actorId: number }
  | { tipo: 'accion'; accion: string };

/** ConfiguracionCotizacionDTO (MA-65). Porcentajes de 0 a 100. */
export interface ConfiguracionCotizacion {
  porcentajeTarifaServicio: number;
  porcentajeDescuentoEstadiaLarga: number;
  minimoNochesDescuento: number;
  actualizadoEn?: string;
}
