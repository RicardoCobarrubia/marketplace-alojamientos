import { useEffect, useState } from 'react';
import { useLocation } from 'react-router';
import { CircleAlert, CircleCheck, LoaderCircle, X } from 'lucide-react';
import { listarAlojamientosAdmin } from '../../api/admin';
import { mensajeParaUsuario } from '../../api/mensajes';
import type { Alojamiento } from '../../types/alojamiento';
import { TablaAlojamientosAdmin } from './TablaAlojamientosAdmin';

type Pestana = 'pendientes' | 'todas';

/** Publicaciones enviadas por los anfitriones (MA-57, RF-12). */
export default function RevisionPublicacionesPage() {
  const ubicacion = useLocation();
  const [pestana, setPestana] = useState<Pestana>('pendientes');
  const [pendientes, setPendientes] = useState<Alojamiento[] | null>(null);
  const [todas, setTodas] = useState<Alojamiento[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(
    () => (ubicacion.state as { aviso?: string } | null)?.aviso ?? null,
  );

  useEffect(() => {
    let activo = true;
    Promise.all([listarAlojamientosAdmin('PENDIENTE_REVISION'), listarAlojamientosAdmin()])
      .then(([listaPendientes, listaTodas]) => {
        if (!activo) return;
        setPendientes(listaPendientes);
        setTodas(listaTodas);
      })
      .catch((e: unknown) => activo && setError(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, []);

  const lista = pestana === 'pendientes' ? pendientes : todas;

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Revisión de publicaciones</h1>
          <p>Publicaciones enviadas por anfitriones</p>
        </div>
      </div>

      {aviso && (
        <div className="alerta alerta--exito" role="status">
          <CircleCheck size={16} aria-hidden="true" />
          <span>{aviso}</span>
          <button type="button" className="alerta__cerrar" onClick={() => setAviso(null)} aria-label="Cerrar aviso">
            <X size={16} />
          </button>
        </div>
      )}
      {error && (
        <div className="alerta alerta--error" role="alert">
          <CircleAlert size={16} aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}

      <div className="filtros" role="tablist" aria-label="Publicaciones">
        <button
          type="button"
          role="tab"
          aria-selected={pestana === 'pendientes'}
          className={pestana === 'pendientes' ? 'chip chip--activo' : 'chip'}
          onClick={() => setPestana('pendientes')}
        >
          Pendientes {pendientes && <span className="chip__contador">{pendientes.length}</span>}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={pestana === 'todas'}
          className={pestana === 'todas' ? 'chip chip--activo' : 'chip'}
          onClick={() => setPestana('todas')}
        >
          Todas {todas && <span className="chip__contador">{todas.length}</span>}
        </button>
      </div>

      <section className="tarjeta">
        {!lista && !error && (
          <div className="cargando">
            <LoaderCircle size={18} className="girando" aria-hidden="true" />
            Cargando publicaciones…
          </div>
        )}
        {lista && (
          <TablaAlojamientosAdmin
            alojamientos={lista}
            modo={pestana === 'pendientes' ? 'revision' : 'catalogo'}
            textoVacio={pestana === 'pendientes' ? 'No hay publicaciones esperando revisión.' : 'Todavía no hay alojamientos.'}
          />
        )}
      </section>
    </>
  );
}
