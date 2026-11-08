import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { iniciarSesion as solicitarLogin } from '../api/auth';
import type { Credenciales } from '../types/auth';
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

  const iniciarSesion = useCallback(async (credenciales: Credenciales) => {
    const respuesta = await solicitarLogin(credenciales);
    const nueva: Sesion = {
      token: respuesta.token,
      expiraEn: respuesta.expiraEn,
      usuario: respuesta.usuario,
    };
    guardarSesion(nueva);
    setSesion(nueva);
    return respuesta.usuario;
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
    () => ({ sesion, usuario: sesion?.usuario ?? null, iniciarSesion, cerrarSesion }),
    [sesion, iniciarSesion, cerrarSesion],
  );

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>;
}
