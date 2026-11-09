import { NOMBRE_ESTADO, type EstadoAlojamiento } from '../types/alojamiento';
import './EtiquetaEstado.css';

export function EtiquetaEstado({ estado }: { estado: EstadoAlojamiento }) {
  return <span className={`etiqueta-estado etiqueta-estado--${estado.toLowerCase()}`}>{NOMBRE_ESTADO[estado]}</span>;
}
