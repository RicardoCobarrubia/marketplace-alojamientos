import type { Alojamiento, DatosAlojamiento, Modalidad } from '../../../types/alojamiento';
import { leerNumero, tieneMaximoDosDecimales } from '../../../utils/formato';

/** Estado del formulario del alojamiento. Los números se guardan como texto mientras se editan. */

export interface AdicionalLocal {
  /** Identificador local para React; `id` solo existe si ya está guardado en el backend. */
  clave: string;
  id?: number;
  nombre: string;
  costo: string;
  modalidad: Modalidad;
}

export interface Formulario {
  titulo: string;
  descripcion: string;
  ciudadId: string;
  tipoId: string;
  ubicacion: string;
  capacidad: string;
  precioNoche: string;
  tarifaLimpieza: string;
  servicioIds: number[];
  /** Una regla por línea. */
  reglas: string;
  imagenes: string[];
  politicaCancelacionId: string;
  adicionales: AdicionalLocal[];
}

export type CampoFormulario = Exclude<keyof Formulario, 'adicionales'>;
export type Errores = Partial<Record<CampoFormulario, string>>;

export const MAXIMO_IMAGENES = 10;
export const MAXIMO_REGLAS = 20;

export const PASOS = ['Información básica', 'Precios y servicios', 'Imágenes y política', 'Revisar y enviar'];

/** Campos que se editan en cada paso: sirve para saltar al paso de un error. */
export const CAMPOS_POR_PASO: CampoFormulario[][] = [
  ['titulo', 'descripcion', 'ciudadId', 'tipoId', 'ubicacion', 'capacidad'],
  ['precioNoche', 'tarifaLimpieza', 'servicioIds', 'reglas'],
  ['imagenes', 'politicaCancelacionId'],
  [],
];

export const FORMULARIO_VACIO: Formulario = {
  titulo: '',
  descripcion: '',
  ciudadId: '',
  tipoId: '',
  ubicacion: '',
  capacidad: '',
  precioNoche: '',
  tarifaLimpieza: '0',
  servicioIds: [],
  reglas: '',
  imagenes: [],
  politicaCancelacionId: '',
  adicionales: [],
};

let contador = 0;
export function nuevaClave(): string {
  contador += 1;
  return `local-${contador}`;
}

export function desdeAlojamiento(a: Alojamiento): Formulario {
  return {
    titulo: a.titulo,
    descripcion: a.descripcion,
    ciudadId: String(a.ciudadId),
    tipoId: String(a.tipoId),
    ubicacion: a.ubicacion,
    capacidad: String(a.capacidad),
    precioNoche: String(a.precioNoche),
    tarifaLimpieza: String(a.tarifaLimpieza),
    servicioIds: [...a.servicioIds],
    reglas: a.reglas.join('\n'),
    imagenes: [...a.imagenes],
    politicaCancelacionId: String(a.politicaCancelacionId),
    adicionales: a.serviciosAdicionales.map((s) => ({
      clave: nuevaClave(),
      id: s.id,
      nombre: s.nombre,
      costo: String(s.costo),
      modalidad: s.modalidad,
    })),
  };
}

export function separarReglas(texto: string): string[] {
  return texto
    .split('\n')
    .map((regla) => regla.trim())
    .filter(Boolean);
}

export function aDatos(f: Formulario): DatosAlojamiento {
  return {
    titulo: f.titulo.trim(),
    descripcion: f.descripcion.trim(),
    ubicacion: f.ubicacion.trim(),
    ciudadId: Number(f.ciudadId),
    tipoId: Number(f.tipoId),
    politicaCancelacionId: Number(f.politicaCancelacionId),
    capacidad: Number(f.capacidad),
    precioNoche: leerNumero(f.precioNoche),
    tarifaLimpieza: leerNumero(f.tarifaLimpieza),
    servicioIds: f.servicioIds,
    reglas: separarReglas(f.reglas),
    imagenes: f.imagenes,
  };
}

export function esUrlImagen(texto: string): boolean {
  try {
    const url = new URL(texto);
    return url.protocol === 'https:' || url.protocol === 'http:';
  } catch {
    return false;
  }
}

/** Valida un precio: positivo (o cero si se permite), máximo 2 decimales y 10 enteros, como el backend. */
export function validarDinero(texto: string, nombre: string, permiteCero: boolean): string | undefined {
  const valor = leerNumero(texto);
  if (Number.isNaN(valor)) return `${nombre} es obligatorio${permiteCero ? ' (puede ser 0)' : ''}.`;
  if (permiteCero ? valor < 0 : valor <= 0) return permiteCero ? `${nombre} no puede ser negativo.` : `${nombre} debe ser positivo.`;
  if (!tieneMaximoDosDecimales(valor)) return `${nombre} admite máximo 2 decimales.`;
  if (valor >= 1e10) return `${nombre} es demasiado alto.`;
  return undefined;
}

/** Mismas reglas que AlojamientoDTO (grupo Datos) en el backend. */
export function validarPaso(paso: number, f: Formulario): Errores {
  const e: Errores = {};
  if (paso === 0) {
    if (!f.titulo.trim()) e.titulo = 'El título es obligatorio.';
    else if (f.titulo.trim().length > 120) e.titulo = 'El título admite máximo 120 caracteres.';
    if (!f.descripcion.trim()) e.descripcion = 'La descripción es obligatoria.';
    else if (f.descripcion.trim().length > 2000) e.descripcion = 'La descripción admite máximo 2000 caracteres.';
    if (!f.ciudadId) e.ciudadId = 'La ciudad es obligatoria.';
    if (!f.tipoId) e.tipoId = 'El tipo de alojamiento es obligatorio.';
    if (!f.ubicacion.trim()) e.ubicacion = 'La ubicación es obligatoria.';
    else if (f.ubicacion.trim().length > 200) e.ubicacion = 'La ubicación admite máximo 200 caracteres.';
    const capacidad = leerNumero(f.capacidad);
    if (!Number.isInteger(capacidad) || capacidad <= 0) e.capacidad = 'La capacidad debe ser un número entero mayor que cero.';
  }
  if (paso === 1) {
    const precio = validarDinero(f.precioNoche, 'El precio por noche', false);
    if (precio) e.precioNoche = precio;
    const limpieza = validarDinero(f.tarifaLimpieza, 'La tarifa de limpieza', true);
    if (limpieza) e.tarifaLimpieza = limpieza;
    if (f.servicioIds.length === 0) e.servicioIds = 'Elige al menos un servicio.';
    const reglas = separarReglas(f.reglas);
    if (reglas.length === 0) e.reglas = 'Agrega al menos una regla.';
    else if (reglas.length > MAXIMO_REGLAS) e.reglas = `Máximo ${MAXIMO_REGLAS} reglas.`;
    else if (reglas.some((r) => r.length > 300)) e.reglas = 'Cada regla admite máximo 300 caracteres.';
  }
  if (paso === 2) {
    if (f.imagenes.length < 1 || f.imagenes.length > MAXIMO_IMAGENES) e.imagenes = 'Agrega entre 1 y 10 imágenes.';
    if (!f.politicaCancelacionId) e.politicaCancelacionId = 'La política de cancelación es obligatoria.';
  }
  return e;
}

export function validarTodo(f: Formulario): Errores {
  return { ...validarPaso(0, f), ...validarPaso(1, f), ...validarPaso(2, f) };
}

export function primerPasoConError(errores: Errores): number {
  const indice = CAMPOS_POR_PASO.findIndex((campos) => campos.some((campo) => errores[campo]));
  return indice === -1 ? 0 : indice;
}

/** Traduce los nombres de campo del backend ("reglas[0]", "ciudadId") a los del formulario. */
export function camposDesdeBackend(errores: Record<string, string>): Errores {
  const resultado: Errores = {};
  for (const [clave, mensaje] of Object.entries(errores)) {
    const base = clave.replace(/\[.*$/, '').replace(/\..*$/, '') as CampoFormulario;
    if (CAMPOS_POR_PASO.flat().includes(base)) resultado[base] ??= mensaje;
  }
  return resultado;
}
