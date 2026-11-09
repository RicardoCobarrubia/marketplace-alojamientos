/** Tipos que reflejan los DTO de alojamientos, catálogos y disponibilidad del backend. */

export type EstadoAlojamiento = 'BORRADOR' | 'PENDIENTE_REVISION' | 'ACTIVA' | 'PAUSADA' | 'BLOQUEADA';

export type Modalidad = 'POR_ESTADIA' | 'POR_NOCHE';

export interface Ciudad {
  id: number;
  nombre: string;
  departamento: string;
}

export interface TipoAlojamiento {
  id: number;
  codigo: string;
  nombre: string;
}

export interface Servicio {
  id: number;
  codigo: string;
  nombre: string;
}

export interface PoliticaCancelacion {
  id: number;
  codigo: string;
  nombre: string;
}

export interface ServicioAdicional {
  id: number;
  nombre: string;
  costo: number;
  modalidad: Modalidad;
}

/** Cuerpo de POST y PUT de servicios adicionales (MA-55). */
export interface DatosServicioAdicional {
  nombre: string;
  costo: number;
  modalidad: Modalidad;
}

/** Respuesta de los endpoints de alojamiento (AlojamientoDTO). */
export interface Alojamiento {
  id: number;
  anfitrionId: number;
  anfitrionNombre: string;
  titulo: string;
  descripcion: string;
  ubicacion: string;
  ciudadId: number;
  ciudad: Ciudad;
  tipoId: number;
  tipo: TipoAlojamiento;
  politicaCancelacionId: number;
  politicaCancelacion: PoliticaCancelacion;
  capacidad: number;
  precioNoche: number;
  tarifaLimpieza: number;
  servicioIds: number[];
  servicios: Servicio[];
  reglas: string[];
  /** URLs de las imágenes; la primera es la principal. */
  imagenes: string[];
  serviciosAdicionales: ServicioAdicional[];
  estado: EstadoAlojamiento;
  motivoRechazo: string | null;
  creadoEn: string;
  actualizadoEn: string;
}

/** Cuerpo de POST /api/alojamientos y PUT /api/alojamientos/{id} (MA-53, MA-54). */
export interface DatosAlojamiento {
  titulo: string;
  descripcion: string;
  ubicacion: string;
  ciudadId: number;
  tipoId: number;
  politicaCancelacionId: number;
  capacidad: number;
  precioNoche: number;
  tarifaLimpieza: number;
  servicioIds: number[];
  reglas: string[];
  imagenes: string[];
}

/** Periodo disponible (MA-58). `fechaFin` es el último día de salida posible. Fechas ISO (2026-12-20). */
export interface PeriodoDisponible {
  id: number;
  fechaInicio: string;
  fechaFin: string;
}

export interface DatosPeriodo {
  fechaInicio: string;
  fechaFin: string;
}

export type MotivoNoDisponible = 'NO_ACTIVO' | 'FUERA_DE_PERIODO' | 'OCUPADO';

/** Respuesta de GET /api/alojamientos/{id}/disponibilidad (MA-59). */
export interface Disponibilidad {
  alojamientoId: number;
  llegada: string;
  salida: string;
  noches: number;
  disponible: boolean;
  motivo: MotivoNoDisponible | null;
}

export const NOMBRE_ESTADO: Record<EstadoAlojamiento, string> = {
  BORRADOR: 'Borrador',
  PENDIENTE_REVISION: 'En revisión',
  ACTIVA: 'Activa',
  PAUSADA: 'Pausada',
  BLOQUEADA: 'Bloqueada',
};

export const NOMBRE_MODALIDAD: Record<Modalidad, string> = {
  POR_ESTADIA: 'Por estadía',
  POR_NOCHE: 'Por noche',
};

/** Nombres legibles de los campos que devuelve INFORMACION_INCOMPLETA. */
export const NOMBRE_CAMPO_ALOJAMIENTO: Record<string, string> = {
  titulo: 'título',
  descripcion: 'descripción',
  ubicacion: 'ubicación',
  tipo: 'tipo de alojamiento',
  ciudad: 'ciudad',
  politicaCancelacion: 'política de cancelación',
  capacidad: 'capacidad',
  precioNoche: 'precio por noche',
  servicios: 'servicios',
  reglas: 'reglas',
  imagenes: 'imágenes',
};
