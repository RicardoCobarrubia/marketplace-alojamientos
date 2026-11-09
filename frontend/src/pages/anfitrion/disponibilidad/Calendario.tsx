import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { PeriodoDisponible } from '../../../types/alojamiento';
import { aIso, formatearMes, hoyIso } from '../../../utils/formato';

const DIAS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

interface PropiedadesCalendario {
  mes: Date;
  periodos: PeriodoDisponible[];
  alCambiarMes: (mes: Date) => void;
}

/**
 * Un día está disponible si alguna noche de un periodo empieza ese día: desde `fechaInicio`
 * hasta el día antes de `fechaFin`, porque `fechaFin` es el último día de salida.
 */
function disponible(iso: string, periodos: PeriodoDisponible[]): boolean {
  return periodos.some((p) => p.fechaInicio <= iso && iso < p.fechaFin);
}

export function Calendario({ mes, periodos, alCambiarMes }: PropiedadesCalendario) {
  const hoy = hoyIso();
  const anio = mes.getFullYear();
  const mesIndice = mes.getMonth();
  const diasEnMes = new Date(anio, mesIndice + 1, 0).getDate();
  const desfase = (new Date(anio, mesIndice, 1).getDay() + 6) % 7; // lunes primero

  const celdas: (string | null)[] = [
    ...Array<null>(desfase).fill(null),
    ...Array.from({ length: diasEnMes }, (_, i) => aIso(new Date(anio, mesIndice, i + 1))),
  ];

  return (
    <section className="tarjeta tarjeta--relleno calendario">
      <div className="calendario__encabezado">
        <h2 className="calendario__mes">{formatearMes(mes)}</h2>
        <div className="calendario__navegacion">
          <button
            type="button"
            className="boton-icono"
            onClick={() => alCambiarMes(new Date(anio, mesIndice - 1, 1))}
            aria-label="Mes anterior"
          >
            <ChevronLeft size={16} />
          </button>
          <button
            type="button"
            className="boton-icono"
            onClick={() => alCambiarMes(new Date(anio, mesIndice + 1, 1))}
            aria-label="Mes siguiente"
          >
            <ChevronRight size={16} />
          </button>
        </div>
      </div>

      <div className="calendario__rejilla" role="grid" aria-label={formatearMes(mes)}>
        {DIAS.map((dia) => (
          <div key={dia} className="calendario__dia-semana" role="columnheader">
            {dia}
          </div>
        ))}
        {celdas.map((iso, indice) => {
          if (!iso) return <div key={`vacio-${indice}`} aria-hidden="true" />;
          const libre = disponible(iso, periodos);
          const pasado = iso < hoy;
          const clase = ['calendario__dia', libre ? 'calendario__dia--disponible' : 'calendario__dia--no-disponible'];
          if (pasado) clase.push('calendario__dia--pasado');
          if (iso === hoy) clase.push('calendario__dia--hoy');
          return (
            <div
              key={iso}
              className={clase.join(' ')}
              role="gridcell"
              aria-label={`${Number(iso.slice(8))}: ${libre ? 'disponible' : 'no disponible'}`}
            >
              {Number(iso.slice(8))}
            </div>
          );
        })}
      </div>

      <div className="calendario__leyenda">
        <span>
          <i className="calendario__muestra calendario__dia--disponible" /> Disponible
        </span>
        <span>
          <i className="calendario__muestra calendario__dia--no-disponible" /> No disponible
        </span>
      </div>
    </section>
  );
}
