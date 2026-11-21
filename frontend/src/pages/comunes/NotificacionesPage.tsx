import { useState } from 'react';
import { CheckCheck, CircleAlert, LoaderCircle } from 'lucide-react';
import { ItemNotificacion } from '../../components/ItemNotificacion';
import { useNotificaciones } from '../../hooks/useNotificaciones';

type Filtro = 'todas' | 'sin-leer';

/** Todas las notificaciones del usuario, para cualquier rol (RF-32). */
export default function NotificacionesPage() {
  const { notificaciones, noLeidas, error, marcarLeida, marcarTodas } = useNotificaciones();
  const [filtro, setFiltro] = useState<Filtro>('todas');
  const [marcando, setMarcando] = useState(false);

  const visibles = notificaciones?.filter((n) => filtro === 'todas' || !n.leidaEn) ?? [];

  async function marcarTodasComoLeidas() {
    setMarcando(true);
    try {
      await marcarTodas();
    } finally {
      setMarcando(false);
    }
  }

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Notificaciones</h1>
          <p>{noLeidas > 0 ? `Tienes ${noLeidas} sin leer` : 'Estás al día'}</p>
        </div>
        {noLeidas > 0 && (
          <button type="button" className="boton boton--secundario" onClick={marcarTodasComoLeidas} disabled={marcando}>
            {marcando ? <LoaderCircle size={15} className="girando" aria-hidden="true" /> : <CheckCheck size={15} aria-hidden="true" />}
            Marcar todas como leídas
          </button>
        )}
      </div>

      {error && (
        <div className="alerta alerta--error" role="alert">
          <CircleAlert size={16} aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}

      <div className="filtros" role="tablist" aria-label="Filtrar notificaciones">
        {(['todas', 'sin-leer'] as Filtro[]).map((f) => (
          <button
            key={f}
            type="button"
            role="tab"
            aria-selected={filtro === f}
            className={filtro === f ? 'chip chip--activo' : 'chip'}
            onClick={() => setFiltro(f)}
          >
            {f === 'todas' ? 'Todas' : 'Sin leer'}
            <span className="chip__contador">{f === 'todas' ? (notificaciones?.length ?? 0) : noLeidas}</span>
          </button>
        ))}
      </div>

      <section className="tarjeta">
        {!notificaciones && !error && (
          <div className="cargando">
            <LoaderCircle size={18} className="girando" aria-hidden="true" />
            Cargando notificaciones…
          </div>
        )}
        {notificaciones && visibles.length === 0 && (
          <div className="estado-vacio">
            {filtro === 'todas' ? 'Todavía no tienes notificaciones.' : 'No tienes notificaciones sin leer.'}
          </div>
        )}
        {visibles.map((n) => (
          <ItemNotificacion key={n.id} notificacion={n} alMarcar={(id) => void marcarLeida(id)} />
        ))}
      </section>
    </>
  );
}
