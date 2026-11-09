import type { Rol } from '../types/auth';

export const RUTAS = {
  login: '/login',
  registro: '/registro',
  recuperarContrasena: '/recuperar-contrasena',
  huesped: '/huesped',
  anfitrion: '/anfitrion',
  admin: '/admin',
} as const;

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
