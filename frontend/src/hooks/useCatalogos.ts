import { useEffect, useState } from 'react';
import { listarCiudades, listarPoliticasCancelacion, listarServicios, listarTipos } from '../api/catalogos';
import { mensajeParaUsuario } from '../api/mensajes';
import type { Ciudad, PoliticaCancelacion, Servicio, TipoAlojamiento } from '../types/alojamiento';

export interface Catalogos {
  ciudades: Ciudad[];
  tipos: TipoAlojamiento[];
  servicios: Servicio[];
  politicas: PoliticaCancelacion[];
}

/** Los catálogos casi no cambian: se cargan una vez por sesión del navegador. */
let cargaEnCurso: Promise<Catalogos> | null = null;

function cargarCatalogos(): Promise<Catalogos> {
  cargaEnCurso ??= Promise.all([listarCiudades(), listarTipos(), listarServicios(), listarPoliticasCancelacion()])
    .then(([ciudades, tipos, servicios, politicas]) => ({ ciudades, tipos, servicios, politicas }))
    .catch((error: unknown) => {
      cargaEnCurso = null; // permite reintentar
      throw error;
    });
  return cargaEnCurso;
}

export function useCatalogos(): { catalogos: Catalogos | null; error: string | null } {
  const [estado, setEstado] = useState<{ catalogos: Catalogos | null; error: string | null }>({
    catalogos: null,
    error: null,
  });

  useEffect(() => {
    let activo = true;
    cargarCatalogos()
      .then((catalogos) => activo && setEstado({ catalogos, error: null }))
      .catch((error: unknown) => activo && setEstado({ catalogos: null, error: mensajeParaUsuario(error) }));
    return () => {
      activo = false;
    };
  }, []);

  return estado;
}
