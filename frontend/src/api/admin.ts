import type { Alojamiento, Ciudad, EstadoAlojamiento, Servicio, TipoAlojamiento } from '../types/alojamiento';
import type {
  ConfiguracionCotizacion,
  FiltroAuditoria,
  Indicadores,
  Ingresos,
  RegistroAuditoria,
  Resena,
  ResultadoModeracion,
} from '../types/admin';
import type { Reserva } from '../types/reserva';
import { peticion, peticionArchivo } from './cliente';
import { peticion } from './cliente';

/** Endpoints del administrador (/api/admin/**). */

// ─── Alojamientos: revisión (MA-57) ─────────────────────

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

// ─── Catálogos (MA-53, escenario 3) ─────────────────────

export function crearCiudad(datos: { nombre: string; departamento: string }): Promise<Ciudad> {
  return peticion<Ciudad>('/api/admin/catalogos/ciudades', { metodo: 'POST', cuerpo: datos });
}

export function crearTipo(datos: { codigo: string; nombre: string }): Promise<TipoAlojamiento> {
  return peticion<TipoAlojamiento>('/api/admin/catalogos/tipos', { metodo: 'POST', cuerpo: datos });
}

export function crearServicio(datos: { codigo: string; nombre: string }): Promise<Servicio> {
  return peticion<Servicio>('/api/admin/catalogos/servicios', { metodo: 'POST', cuerpo: datos });
}

// ─── Panel (MA-83, MA-84) ───────────────────────────────

export function obtenerIndicadores(): Promise<Indicadores> {
  return peticion<Indicadores>('/api/admin/panel/indicadores');
}

/** Sin fechas, el backend usa los últimos 30 días. */
export function obtenerIngresos(desde?: string, hasta?: string): Promise<Ingresos> {
  const parametros = new URLSearchParams();
  if (desde) parametros.set('desde', desde);
  if (hasta) parametros.set('hasta', hasta);
  const consulta = parametros.toString() ? `?${parametros}` : '';
  return peticion<Ingresos>(`/api/admin/panel/ingresos${consulta}`);
}

// ─── Moderación (MA-80, MA-81, MA-82) ───────────────────

/** Bloquea el alojamiento; sus reservas futuras pasan a EN_REVISION. */
export function bloquearAlojamiento(id: number, motivo: string): Promise<ResultadoModeracion> {
  return peticion<ResultadoModeracion>(`/api/admin/alojamientos/${id}/bloqueo`, { metodo: 'POST', cuerpo: { motivo } });
}

/** BLOQUEADA → PAUSADA. */
export function desbloquearAlojamiento(id: number): Promise<ResultadoModeracion> {
  return peticion<ResultadoModeracion>(`/api/admin/alojamientos/${id}/desbloqueo`, { metodo: 'POST' });
}

/** Cierra sus sesiones, pausa sus alojamientos y envía a revisión sus reservas futuras. */
export function restringirUsuario(id: number, motivo: string): Promise<ResultadoModeracion> {
  return peticion<ResultadoModeracion>(`/api/admin/usuarios/${id}/restriccion`, { metodo: 'POST', cuerpo: { motivo } });
}

export function levantarRestriccion(id: number): Promise<ResultadoModeracion> {
  return peticion<ResultadoModeracion>(`/api/admin/usuarios/${id}/restriccion`, { metodo: 'DELETE' });
}

export function listarReservasEnRevision(): Promise<Reserva[]> {
  return peticion<Reserva[]>('/api/admin/reservas/en-revision');
}

/** EN_REVISION → CONFIRMADA. */
export function mantenerReserva(id: number): Promise<Reserva> {
  return peticion<Reserva>(`/api/admin/reservas/${id}/mantener`, { metodo: 'POST' });
}

/** Cancela la reserva con devolución del 100 %. */
export function cancelarReservaAdmin(id: number, motivo: string): Promise<Reserva> {
  return peticion<Reserva>(`/api/admin/reservas/${id}/cancelacion`, { metodo: 'POST', cuerpo: { motivo } });
}

// ─── Reseñas (MA-79) ────────────────────────────────────

/** Sin filtro, todas; con `oculta`, solo las ocultas (true) o las visibles (false). */
export function listarResenasAdmin(oculta?: boolean): Promise<Resena[]> {
  const consulta = oculta === undefined ? '' : `?oculta=${oculta}`;
  return peticion<Resena[]>(`/api/admin/resenas${consulta}`);
}

export function ocultarResena(id: number, motivo: string): Promise<Resena> {
  return peticion<Resena>(`/api/admin/resenas/${id}/ocultamiento`, { metodo: 'POST', cuerpo: { motivo } });
}

export function mostrarResena(id: number): Promise<Resena> {
  return peticion<Resena>(`/api/admin/resenas/${id}/ocultamiento`, { metodo: 'DELETE' });
}

// ─── Auditoría (MA-87) ──────────────────────────────────

/** Los 200 registros más recientes, con un solo filtro a la vez. */
export function consultarAuditoria(filtro: FiltroAuditoria): Promise<RegistroAuditoria[]> {
  const parametros = new URLSearchParams();
  if (filtro.tipo === 'entidad') {
    parametros.set('entidad', filtro.entidad);
    parametros.set('entidadId', String(filtro.entidadId));
  } else if (filtro.tipo === 'actor') {
    parametros.set('actorId', String(filtro.actorId));
  } else if (filtro.tipo === 'accion') {
    parametros.set('accion', filtro.accion);
  }
  const consulta = parametros.toString() ? `?${parametros}` : '';
  return peticion<RegistroAuditoria[]>(`/api/admin/auditoria${consulta}`);
}

// ─── Tarifas de la cotización (MA-65) ───────────────────

export function obtenerConfiguracionCotizacion(): Promise<ConfiguracionCotizacion> {
  return peticion<ConfiguracionCotizacion>('/api/admin/configuracion/cotizacion');
}

export function actualizarConfiguracionCotizacion(datos: ConfiguracionCotizacion): Promise<ConfiguracionCotizacion> {
  return peticion<ConfiguracionCotizacion>('/api/admin/configuracion/cotizacion', { metodo: 'PUT', cuerpo: datos });
}

// ─── Reportes de gestión (MA-85, MA-86) ─────────────────

export type FormatoReporte = 'PDF' | 'EXCEL';

/**
 * Genera el reporte de gestión del periodo (máximo 366 días) y lo devuelve como archivo. El
 * backend registra en la auditoría quién lo pidió y cuándo.
 */
export function exportarReporte(formato: FormatoReporte, desde: string, hasta: string): Promise<Blob> {
  const parametros = new URLSearchParams({ formato, desde, hasta });
  return peticionArchivo(`/api/admin/reportes?${parametros}`);
}

/** Mismo nombre que arma el backend: el navegador no puede leer Content-Disposition por CORS. */
export function nombreReporte(formato: FormatoReporte, desde: string, hasta: string): string {
  return `reporte-gestion-${desde}-a-${hasta}.${formato === 'PDF' ? 'pdf' : 'xlsx'}`;
}
