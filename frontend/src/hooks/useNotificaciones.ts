import { useCallback, useEffect, useState } from 'react';
import { mensajeParaUsuario } from '../api/mensajes';
import { listarNotificaciones, marcarNotificacionLeida } from '../api/notificaciones';
import type { Notificacion } from '../types/notificacion';

/** Cada cuánto se buscan notificaciones nuevas. */
const INTERVALO_MS = 60_000;
/** Avisa a las demás vistas (campana, página, menú) que algo cambió para que recarguen. */
const EVENTO_CAMBIO = 'lr:notificaciones';

export interface EstadoNotificaciones {
  notificaciones: Notificacion[] | null;
  noLeidas: number;
  error: string | null;
  recargar: () => void;
  marcarLeida: (id: number) => Promise<void>;
  marcarTodas: () => Promise<void>;
}

/**
 * Notificaciones del usuario con sesión. Se recargan cada minuto, al volver a la pestaña y cuando
 * otra parte de la app marca alguna como leída.
 */
export function useNotificaciones(): EstadoNotificaciones {
  const [notificaciones, setNotificaciones] = useState<Notificacion[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const recargar = useCallback(() => {
    listarNotificaciones()
      .then((lista) => {
        setNotificaciones(lista);
        setError(null);
      })
      .catch((e: unknown) => setError(mensajeParaUsuario(e)));
  }, []);

  useEffect(() => {
    recargar();
    const intervalo = window.setInterval(recargar, INTERVALO_MS);
    const alVolver = () => document.visibilityState === 'visible' && recargar();
    window.addEventListener(EVENTO_CAMBIO, recargar);
    document.addEventListener('visibilitychange', alVolver);
    return () => {
      window.clearInterval(intervalo);
      window.removeEventListener(EVENTO_CAMBIO, recargar);
      document.removeEventListener('visibilitychange', alVolver);
    };
  }, [recargar]);

  const marcarLeida = useCallback(async (id: number) => {
    const actualizada = await marcarNotificacionLeida(id);
    setNotificaciones((lista) => lista?.map((n) => (n.id === id ? actualizada : n)) ?? null);
    window.dispatchEvent(new Event(EVENTO_CAMBIO));
  }, []);

  const marcarTodas = useCallback(async () => {
    // El backend marca de a una: se envían en paralelo las que faltan.
    const pendientes = notificaciones?.filter((n) => !n.leidaEn) ?? [];
    const actualizadas = await Promise.all(pendientes.map((n) => marcarNotificacionLeida(n.id)));
    const porId = new Map(actualizadas.map((n) => [n.id, n]));
    setNotificaciones((lista) => lista?.map((n) => porId.get(n.id) ?? n) ?? null);
    window.dispatchEvent(new Event(EVENTO_CAMBIO));
  }, [notificaciones]);

  return {
    notificaciones,
    noLeidas: notificaciones?.filter((n) => !n.leidaEn).length ?? 0,
    error,
    recargar,
    marcarLeida,
    marcarTodas,
  };
}
