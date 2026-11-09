import { useEffect, useMemo, useState } from 'react';
import { CircleAlert, LoaderCircle } from 'lucide-react';
import { listarAlojamientosAdmin } from '../../api/admin';
import { mensajeParaUsuario } from '../../api/mensajes';
import { NOMBRE_ESTADO, type Alojamiento, type EstadoAlojamiento } from '../../types/alojamiento';
import { TablaAlojamientosAdmin } from './TablaAlojamientosAdmin';

type Filtro = EstadoAlojamiento | 'TODOS';
const FILTROS: Filtro[] = ['TODOS', 'BORRADOR', 'PENDIENTE_REVISION', 'ACTIVA', 'PAUSADA', 'BLOQUEADA'];

/** Todos los alojamientos de la plataforma, por estado (RF-10: el administrador los ve todos). */
export default function AlojamientosAdminPage() {
  const [alojamientos, setAlojamientos] = useState<Alojamiento[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [filtro, setFiltro] = useState<Filtro>('TODOS');

  useEffect(() => {
    let activo = true;
    listarAlojamientosAdmin()
      .then((lista) => activo && setAlojamientos(lista))
      .catch((e: unknown) => activo && setError(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, []);

  const conteo = useMemo(() => {
    const resultado: Partial<Record<Filtro, number>> = { TODOS: alojamientos?.length ?? 0 };
    alojamientos?.forEach((a) => (resultado[a.estado] = (resultado[a.estado] ?? 0) + 1));
    return resultado;
  }, [alojamientos]);

  const visibles = alojamientos?.filter((a) => filtro === 'TODOS' || a.estado === filtro) ?? [];

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Alojamientos</h1>
          <p>Todas las publicaciones de la plataforma</p>
        </div>
      </div>

      {error && (
        <div className="alerta alerta--error" role="alert">
          <CircleAlert size={16} aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}

      <div className="filtros" role="group" aria-label="Filtrar por estado">
        {FILTROS.map((f) => (
          <button
            key={f}
            type="button"
            className={filtro === f ? 'chip chip--activo' : 'chip'}
            aria-pressed={filtro === f}
            onClick={() => setFiltro(f)}
          >
            {f === 'TODOS' ? 'Todos' : NOMBRE_ESTADO[f]}
            <span className="chip__contador">{conteo[f] ?? 0}</span>
          </button>
        ))}
      </div>

      <section className="tarjeta">
        {!alojamientos && !error && (
          <div className="cargando">
            <LoaderCircle size={18} className="girando" aria-hidden="true" />
            Cargando alojamientos…
          </div>
        )}
        {alojamientos && (
          <TablaAlojamientosAdmin
            alojamientos={visibles}
            modo="catalogo"
            textoVacio={filtro === 'TODOS' ? 'Todavía no hay alojamientos.' : `No hay alojamientos en estado «${NOMBRE_ESTADO[filtro]}».`}
          />
        )}
      </section>
    </>
  );
}
