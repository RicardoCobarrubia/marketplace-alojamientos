import type { Rol } from '../types/auth';

export const RUTAS = {
  login: '/login',
  registro: '/registro',
  recuperarContrasena: '/recuperar-contrasena',
  huesped: '/huesped',
  misReservas: '/huesped/reservas',
  anfitrion: '/anfitrion',
  nuevoAlojamiento: '/anfitrion/alojamientos/nuevo',
  disponibilidad: '/anfitrion/disponibilidad',
  reservasRecibidas: '/anfitrion/reservas',
  admin: '/admin',
  adminPublicaciones: '/admin/publicaciones',
  adminAlojamientos: '/admin/alojamientos',
  adminCatalogos: '/admin/catalogos',
  adminReservasRevision: '/admin/reservas-en-revision',
  adminResenas: '/admin/resenas',
  adminTarifas: '/admin/tarifas',
  adminAuditoria: '/admin/auditoria',
  adminReportes: '/admin/reportes',
} as const;

export function rutaEditarAlojamiento(id: number): string {
  return `/anfitrion/alojamientos/${id}/editar`;
}

export function rutaDisponibilidad(alojamientoId: number): string {
  return `${RUTAS.disponibilidad}?alojamiento=${alojamientoId}`;
}

export function rutaDetallePublicacion(id: number): string {
  return `${RUTAS.adminPublicaciones}/${id}`;
}

/** Página a la que llega cada rol después de iniciar sesión (RF-02). */
export function rutaInicio(rol: Rol): string {
  switch (rol) {
    case 'HUESPED':
      return RUTAS.huesped;
    case 'ANFITRION':
      return RUTAS.anfitrion;
    case 'ADMINISTRADOR':
      return RUTAS.admin;
  }
}

/** Página de notificaciones del rol: /huesped/notificaciones, /anfitrion/notificaciones... */
export function rutaNotificaciones(rol: Rol): string {
  return `${rutaInicio(rol)}/notificaciones`;
}

/** Página "Mi perfil" del rol. */
export function rutaPerfil(rol: Rol): string {
  return `${rutaInicio(rol)}/perfil`;
}

export function rutaDetalleReservaAnfitrion(id: number): string {
  return `${RUTAS.reservasRecibidas}/${id}`;
}

export function rutaReservaHuesped(id: number): string {
  return `${RUTAS.huesped}/reservas/${id}`;
}

/** Fechas y huéspedes que se pasan del buscador al detalle para cotizar de una vez. */
export interface DatosEstadia {
  llegada?: string;
  salida?: string;
  huespedes?: number;
}

/** Detalle de un alojamiento publicado, desde donde el huésped cotiza y reserva. */
export function rutaAlojamientoHuesped(id: number, estadia: DatosEstadia = {}): string {
  const parametros = new URLSearchParams();
  if (estadia.llegada && estadia.salida) {
    parametros.set('llegada', estadia.llegada);
    parametros.set('salida', estadia.salida);
  }
  if (estadia.huespedes) parametros.set('huespedes', String(estadia.huespedes));
  const consulta = parametros.toString();
  return `${RUTAS.huesped}/alojamientos/${id}${consulta ? `?${consulta}` : ''}`;
}
