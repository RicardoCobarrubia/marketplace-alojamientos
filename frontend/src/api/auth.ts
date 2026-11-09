import type { Credenciales, RespuestaLogin } from '../types/auth';
import { peticion } from './cliente';

/** POST /api/auth/login (MA-48). */
export function iniciarSesion(credenciales: Credenciales): Promise<RespuestaLogin> {
  return peticion<RespuestaLogin>('/api/auth/login', {
    metodo: 'POST',
    cuerpo: credenciales,
    autenticado: false,
  });
}
