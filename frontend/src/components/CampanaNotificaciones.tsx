import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { Bell } from 'lucide-react';
import { useNotificaciones } from '../hooks/useNotificaciones';
import { ItemNotificacion } from './ItemNotificacion';
import './Notificaciones.css';

const MAXIMO_EN_PANEL = 6;

/** La campanita de la barra superior con las notificaciones más recientes (RF-32). */
export function CampanaNotificaciones({ rutaTodas }: { rutaTodas: string }) {
  const { notificaciones, noLeidas, error, marcarLeida, marcarTodas } = useNotificaciones();
  const [abierta, setAbierta] = useState(false);
  const contenedor = useRef<HTMLDivElement>(null);

  // Se cierra al hacer clic afuera o con Escape.
  useEffect(() => {
    if (!abierta) return;
    const alHacerClic = (evento: MouseEvent) => {
      if (!contenedor.current?.contains(evento.target as Node)) setAbierta(false);
    };
    const alPresionar = (evento: KeyboardEvent) => evento.key === 'Escape' && setAbierta(false);
    document.addEventListener('mousedown', alHacerClic);
    document.addEventListener('keydown', alPresionar);
    return () => {
      document.removeEventListener('mousedown', alHacerClic);
      document.removeEventListener('keydown', alPresionar);
    };
  }, [abierta]);

  const recientes = notificaciones?.slice(0, MAXIMO_EN_PANEL) ?? [];

  return (
    <div className="campana" ref={contenedor}>
      <button
        type="button"
        className="boton-icono campana__boton"
        onClick={() => setAbierta((a) => !a)}
        aria-expanded={abierta}
        aria-haspopup="dialog"
        aria-label={noLeidas > 0 ? `Notificaciones: ${noLeidas} sin leer` : 'Notificaciones'}
      >
        <Bell size={17} aria-hidden="true" />
        {noLeidas > 0 && <span className="campana__contador">{noLeidas > 9 ? '9+' : noLeidas}</span>}
      </button>

      {abierta && (
        <div className="campana__panel" role="dialog" aria-label="Notificaciones">
          <div className="campana__encabezado">
            <strong>Notificaciones</strong>
            {noLeidas > 0 && (
              <button type="button" className="enlace-accion campana__marcar" onClick={() => void marcarTodas()}>
                Marcar todas como leídas
              </button>
            )}
          </div>
          {error && <p className="campana__vacio">{error}</p>}
          {!error && notificaciones?.length === 0 && <p className="campana__vacio">No tienes notificaciones.</p>}
          <div className="campana__lista">
            {recientes.map((n) => (
              <ItemNotificacion key={n.id} notificacion={n} alMarcar={(id) => void marcarLeida(id)} compacto />
            ))}
          </div>
          <Link to={rutaTodas} className="campana__todas" onClick={() => setAbierta(false)}>
            Ver todas
          </Link>
        </div>
      )}
    </div>
  );
}
