import type { Credenciales, DatosRegistro, RespuestaLogin, Usuario } from '../types/auth';
import { peticion } from './cliente';

/** POST /api/auth/registro (MA-47). Devuelve el usuario creado, sin token. */
export function registrarse(datos: DatosRegistro): Promise<Usuario> {
  return peticion<Usuario>('/api/auth/registro', {
    metodo: 'POST',
    cuerpo: datos,
    autenticado: false,
  });
}

/** POST /api/auth/login (MA-48). */
export function iniciarSesion(credenciales: Credenciales): Promise<RespuestaLogin> {
  return peticion<RespuestaLogin>('/api/auth/login', {
    metodo: 'POST',
    cuerpo: credenciales,
    autenticado: false,
  });
}

/** POST /api/auth/reactivacion (MA-52): reactiva una cuenta desactivada y devuelve el token. */
export function reactivarCuenta(credenciales: Credenciales): Promise<RespuestaLogin> {
  return peticion<RespuestaLogin>('/api/auth/reactivacion', {
    metodo: 'POST',
    cuerpo: credenciales,
    autenticado: false,
  });
}

/**
 * POST /api/auth/recuperacion (MA-50, escenario 1): pide un código de 6 dígitos que llega al
 * correo y vence en 15 minutos. Responde lo mismo exista o no la cuenta.
 */
export function solicitarCodigoRecuperacion(email: string): Promise<{ mensaje: string }> {
  return peticion<{ mensaje: string }>('/api/auth/recuperacion', {
    metodo: 'POST',
    cuerpo: { email },
    autenticado: false,
  });
}

/** POST /api/auth/recuperacion/confirmacion (MA-50, escenario 2): define la nueva contraseña con el código. */
export function restablecerContrasena(email: string, codigo: string, password: string): Promise<void> {
  return peticion<void>('/api/auth/recuperacion/confirmacion', {
    metodo: 'POST',
    cuerpo: { email, codigo, password },
    autenticado: false,
  });
}
