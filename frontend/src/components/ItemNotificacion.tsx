import type { Notificacion } from '../types/notificacion';
import { formatearHace } from '../utils/formato';
import './Notificaciones.css';

interface PropiedadesItem {
  notificacion: Notificacion;
  alMarcar: (id: number) => void;
  compacto?: boolean;
}

/** Una notificación; al hacer clic en una no leída se marca como leída. */
export function ItemNotificacion({ notificacion: n, alMarcar, compacto }: PropiedadesItem) {
  const leida = n.leidaEn !== null;
  const clase = ['notificacion', leida ? 'notificacion--leida' : 'notificacion--nueva', compacto ? 'notificacion--compacta' : '']
    .filter(Boolean)
    .join(' ');

  return (
    <button
      type="button"
      className={clase}
      onClick={() => !leida && alMarcar(n.id)}
      aria-label={`${n.titulo}. ${n.mensaje}. ${leida ? 'Leída' : 'Sin leer: haz clic para marcarla como leída'}`}
    >
      {!leida && <span className="notificacion__punto" aria-hidden="true" />}
      <span className="notificacion__texto">
        <span className="notificacion__titulo">{n.titulo}</span>
        <span className="notificacion__mensaje">{n.mensaje}</span>
        <span className="notificacion__fecha">{formatearHace(n.creadaEn)}</span>
      </span>
    </button>
  );
}
