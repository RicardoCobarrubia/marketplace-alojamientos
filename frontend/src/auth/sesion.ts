import type { Usuario } from '../types/auth';

/**
 * Persistencia de la sesión en el navegador. El backend no guarda sesión (ADR-008): todo lo que
 * el frontend necesita es el token, su vencimiento y los datos del usuario.
 */
export interface Sesion {
  token: string;
  expiraEn: string;
  usuario: Usuario;
}

const CLAVE_SESION = 'lr.sesion';

/** Evento que avisa a la app que el backend rechazó el token (vencido o revocado). */
export const EVENTO_SESION_PERDIDA = 'lr:sesion-perdida';

export function sesionVencida(sesion: Sesion): boolean {
  const vence = Date.parse(sesion.expiraEn);
  return Number.isNaN(vence) || vence <= Date.now();
}

export function leerSesion(): Sesion | null {
  try {
    const texto = localStorage.getItem(CLAVE_SESION);
    if (!texto) return null;
    const sesion = JSON.parse(texto) as Sesion;
    if (!sesion?.token || !sesion.usuario || sesionVencida(sesion)) {
      borrarSesion();
      return null;
    }
    return sesion;
  } catch {
    return null;
  }
}

export function guardarSesion(sesion: Sesion): void {
  try {
    localStorage.setItem(CLAVE_SESION, JSON.stringify(sesion));
  } catch {
    // Almacenamiento bloqueado (modo privado, etc.): la sesión vive solo en memoria.
  }
}

export function borrarSesion(): void {
  try {
    localStorage.removeItem(CLAVE_SESION);
  } catch {
    // Nada que borrar.
  }
}

export function esClaveSesion(clave: string | null): boolean {
  return clave === CLAVE_SESION;
}

export function notificarSesionPerdida(): void {
  window.dispatchEvent(new Event(EVENTO_SESION_PERDIDA));
}
