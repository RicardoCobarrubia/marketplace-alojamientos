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
