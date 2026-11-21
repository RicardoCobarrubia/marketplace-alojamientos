import { useEffect, useState } from 'react';
import { obtenerAlojamiento } from '../api/alojamientos';
import type { Alojamiento } from '../types/alojamiento';
import type { Reserva } from '../types/reserva';

/**
 * ReservaDTO solo trae el id del alojamiento: este hook consulta cada alojamiento una vez para
 * mostrar su título y su foto. Si uno ya no está publicado el backend responde 404 y la página
 * muestra "Alojamiento #id" en su lugar.
 */
export function useAlojamientosDeReservas(reservas: Reserva[] | null): Map<number, Alojamiento> {
  const [alojamientos, setAlojamientos] = useState<Map<number, Alojamiento>>(new Map());

  useEffect(() => {
    if (!reservas) return;
    let activo = true;
    const ids = [...new Set(reservas.map((r) => r.alojamientoId))];
    Promise.allSettled(ids.map((id) => obtenerAlojamiento(id))).then((resultados) => {
      if (!activo) return;
      const encontrados = resultados.flatMap((r) => (r.status === 'fulfilled' ? [r.value] : []));
      setAlojamientos(new Map(encontrados.map((a) => [a.id, a])));
    });
    return () => {
      activo = false;
    };
  }, [reservas]);

  return alojamientos;
}
