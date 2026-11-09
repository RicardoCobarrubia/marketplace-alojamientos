import type { Rol } from '../types/auth';

export const RUTAS = {
  login: '/login',
  registro: '/registro',
  recuperarContrasena: '/recuperar-contrasena',
  huesped: '/huesped',
  anfitrion: '/anfitrion',
  nuevoAlojamiento: '/anfitrion/alojamientos/nuevo',
  disponibilidad: '/anfitrion/disponibilidad',
  reservasRecibidas: '/anfitrion/reservas',
  admin: '/admin',
  adminPublicaciones: '/admin/publicaciones',
  adminAlojamientos: '/admin/alojamientos',
  adminCatalogos: '/admin/catalogos',
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
