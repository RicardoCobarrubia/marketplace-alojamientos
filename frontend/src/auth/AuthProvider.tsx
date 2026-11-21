import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { reactivarCuenta as solicitarReactivacion, iniciarSesion as solicitarLogin } from '../api/auth';
import type { Credenciales, RespuestaLogin, Usuario } from '../types/auth';
import { AuthContext, type ValorAuth } from './contexto';
import {
  borrarSesion,
  esClaveSesion,
  EVENTO_SESION_PERDIDA,
  guardarSesion,
  leerSesion,
  type Sesion,
} from './sesion';

/** setTimeout no admite más de ~24,8 días; el token dura horas, pero se acota por seguridad. */
const MAXIMO_TIMEOUT_MS = 2_147_483_647;

export function AuthProvider({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState<Sesion | null>(() => leerSesion());

  const cerrarSesion = useCallback(() => {
    borrarSesion();
    setSesion(null);
  }, []);

  /** Guarda la sesión que devuelve el login o la reactivación. */
  const abrirSesion = useCallback((respuesta: RespuestaLogin) => {
    const nueva: Sesion = {
      token: respuesta.token,
      expiraEn: respuesta.expiraEn,
      usuario: respuesta.usuario,
    };
    guardarSesion(nueva);
    setSesion(nueva);
    return respuesta.usuario;
  }, []);

  const iniciarSesion = useCallback(
    async (credenciales: Credenciales) => abrirSesion(await solicitarLogin(credenciales)),
    [abrirSesion],
  );

  const reactivarCuenta = useCallback(
    async (credenciales: Credenciales) => abrirSesion(await solicitarReactivacion(credenciales)),
    [abrirSesion],
  );

  const actualizarUsuario = useCallback((usuario: Usuario) => {
    setSesion((actual) => {
      if (!actual) return actual;
      const nueva = { ...actual, usuario };
      guardarSesion(nueva);
      return nueva;
    });
  }, []);

  // Cierra la sesión cuando el backend rechaza el token o cuando se cierra en otra pestaña.
  useEffect(() => {
    const alPerderSesion = () => cerrarSesion();
    const alCambiarAlmacenamiento = (evento: StorageEvent) => {
      if (esClaveSesion(evento.key)) setSesion(leerSesion());
    };
    window.addEventListener(EVENTO_SESION_PERDIDA, alPerderSesion);
    window.addEventListener('storage', alCambiarAlmacenamiento);
    return () => {
      window.removeEventListener(EVENTO_SESION_PERDIDA, alPerderSesion);
      window.removeEventListener('storage', alCambiarAlmacenamiento);
    };
  }, [cerrarSesion]);

  // Cierra la sesión justo cuando vence el token.
  useEffect(() => {
    if (!sesion) return;
    const restante = Date.parse(sesion.expiraEn) - Date.now();
    const temporizador = window.setTimeout(cerrarSesion, Math.min(Math.max(restante, 0), MAXIMO_TIMEOUT_MS));
    return () => window.clearTimeout(temporizador);
  }, [sesion, cerrarSesion]);

  const valor = useMemo<ValorAuth>(
    () => ({
      sesion,
      usuario: sesion?.usuario ?? null,
      iniciarSesion,
      reactivarCuenta,
      actualizarUsuario,
      cerrarSesion,
    }),
    [sesion, iniciarSesion, reactivarCuenta, actualizarUsuario, cerrarSesion],
  );

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>;
}
