import { NOMBRE_ESTADO_RESERVA, type EstadoReserva } from '../types/reserva';
import './EtiquetaEstado.css';

/** Etiqueta de color para el estado de una reserva; reutiliza los estilos de EtiquetaEstado. */
export function EtiquetaReserva({ estado }: { estado: EstadoReserva }) {
  return (
    <span className={`etiqueta-estado etiqueta-reserva--${estado.toLowerCase()}`}>{NOMBRE_ESTADO_RESERVA[estado]}</span>
  );
}
