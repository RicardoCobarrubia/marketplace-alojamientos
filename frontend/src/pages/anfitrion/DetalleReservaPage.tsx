import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ArrowLeft, CircleAlert, Info, LoaderCircle, TriangleAlert } from 'lucide-react';
import { obtenerAlojamiento } from '../../api/alojamientos';
import { mensajeParaUsuario } from '../../api/mensajes';
import { obtenerReserva } from '../../api/reservas';
import { DesgloseReserva } from '../../components/DesgloseReserva';
import { EtiquetaReserva } from '../../components/EtiquetaReserva';
import { Miniatura } from '../../components/Miniatura';
import { RUTAS, rutaDisponibilidad } from '../../routes/rutas';
import type { Alojamiento } from '../../types/alojamiento';
import type { Reserva } from '../../types/reserva';
import { formatearFecha, formatearHace, formatearPrecio } from '../../utils/formato';
import './ReservasRecibidasPage.css';

const FECHA_HORA = new Intl.DateTimeFormat('es-CO', { dateStyle: 'long', timeStyle: 'short' });

/** Detalle de una reserva recibida por el anfitrión (MA-70, RF-26). Solo consulta. */
export default function DetalleReservaPage() {
  const { id } = useParams();
  const [reserva, setReserva] = useState<Reserva | null>(null);
  const [alojamiento, setAlojamiento] = useState<Alojamiento | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let activo = true;
    obtenerReserva(Number(id))
      .then(async (r) => {
        if (!activo) return;
        setReserva(r);
        setAlojamiento(await obtenerAlojamiento(r.alojamientoId));
      })
      .catch((e: unknown) => activo && setError(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, [id]);

  const volver = (
    <Link to={RUTAS.reservasRecibidas} className="enlace formulario__volver">
      <ArrowLeft size={14} aria-hidden="true" />
      Reservas recibidas
    </Link>
  );

  if (error) {
    return (
      <>
        {volver}
        <div className="alerta alerta--error" role="alert">
          <CircleAlert size={16} aria-hidden="true" />
          <span>{error}</span>
        </div>
      </>
    );
  }

  if (!reserva) {
    return (
      <div className="cargando">
        <LoaderCircle size={18} className="girando" aria-hidden="true" />
        Cargando reserva…
      </div>
    );
  }

  const r = reserva;

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          {volver}
          <h1>Reserva #{r.id}</h1>
          <p>Hecha {formatearHace(r.creadoEn)}</p>
        </div>
        <EtiquetaReserva estado={r.estado} />
      </div>

      <div className="detalle-reserva">
        <div className="detalle-reserva__principal">
          <section className="tarjeta tarjeta--relleno">
            <div className="detalle-reserva__alojamiento">
              <Miniatura url={alojamiento?.imagenes[0]} alt={alojamiento?.titulo ?? ''} />
              <div>
                <div className="detalle-reserva__titulo">{alojamiento?.titulo ?? `Alojamiento #${r.alojamientoId}`}</div>
                {alojamiento && (
                  <div className="campo__ayuda">
                    {alojamiento.ciudad.nombre}, {alojamiento.ciudad.departamento} ·{' '}
                    <Link to={rutaDisponibilidad(alojamiento.id)} className="enlace">
                      Ver disponibilidad
                    </Link>
                  </div>
                )}
              </div>
            </div>

            <dl className="detalle-reserva__datos">
              <div>
                <dt>Llegada</dt>
                <dd>{formatearFecha(r.llegada)}</dd>
              </div>
              <div>
                <dt>Salida</dt>
                <dd>{formatearFecha(r.salida)}</dd>
              </div>
              <div>
                <dt>Noches</dt>
                <dd>{r.noches}</dd>
              </div>
              <div>
                <dt>Huéspedes</dt>
                <dd>{r.huespedes}</dd>
              </div>
              <div>
                <dt>Huésped</dt>
                <dd>Usuario #{r.huespedId}</dd>
              </div>
              <div>
                <dt>Reservada el</dt>
                <dd>{FECHA_HORA.format(new Date(r.creadoEn))}</dd>
              </div>
            </dl>
          </section>

          <EstadoDeLaReserva reserva={r} />
        </div>

        <aside className="tarjeta tarjeta--relleno">
          <h2 className="tarjeta__titulo">Desglose del pago</h2>
          <DesgloseReserva reserva={r} paraAnfitrion />
          <p className="campo__ayuda detalle-reserva__nota">
            Son los precios del momento de la reserva: no cambian si después editas el alojamiento.
          </p>
        </aside>
      </div>
    </>
  );
}

/** Explica qué pasa con la reserva según su estado (RF-27 a RF-30). */
function EstadoDeLaReserva({ reserva: r }: { reserva: Reserva }) {
  switch (r.estado) {
    case 'CANCELADA':
      return (
        <section className="tarjeta tarjeta--relleno">
          <h2 className="tarjeta__titulo">Cancelación</h2>
          <dl className="detalle-reserva__datos detalle-reserva__datos--sin-borde">
            {r.canceladaEn && (
              <div>
                <dt>Cancelada el</dt>
                <dd>{FECHA_HORA.format(new Date(r.canceladaEn))}</dd>
              </div>
            )}
            <div>
              <dt>Devolución al huésped</dt>
              <dd>
                {r.valorDevolucion !== null ? formatearPrecio(r.valorDevolucion) : '—'}
                {r.devolucionPendiente && ' (en trámite)'}
              </dd>
            </div>
          </dl>
          {r.motivoCancelacion && <p className="moderacion__motivo">Motivo: {r.motivoCancelacion}</p>}
          <p className="campo__ayuda detalle-reserva__nota">Las fechas quedaron libres para nuevas reservas.</p>
        </section>
      );
    case 'EN_REVISION':
      return (
        <div className="alerta alerta--aviso" role="status">
          <TriangleAlert size={16} aria-hidden="true" />
          <span>
            Un administrador está revisando esta reserva por una medida de moderación. Puede mantenerla o cancelarla con
            devolución del 100 %.
          </span>
        </div>
      );
    case 'PAGO_FALLIDO':
      return (
        <div className="alerta alerta--error" role="status">
          <CircleAlert size={16} aria-hidden="true" />
          <span>El pago no se completó{r.motivoFallo ? `: ${r.motivoFallo}` : ''}. Las fechas quedaron libres.</span>
        </div>
      );
    case 'PENDIENTE_PAGO':
      return (
        <div className="alerta alerta--info" role="status">
          <Info size={16} aria-hidden="true" />
          <span>El pago se está procesando. Las fechas quedan retenidas mientras tanto.</span>
        </div>
      );
    case 'COMPLETADA':
      return (
        <div className="alerta alerta--exito" role="status">
          <Info size={16} aria-hidden="true" />
          <span>La estadía terminó. El huésped puede dejar una reseña durante los 30 días siguientes a la salida.</span>
        </div>
      );
    case 'CONFIRMADA':
      return (
        <div className="alerta alerta--info" role="status">
          <Info size={16} aria-hidden="true" />
          <span>
            Reserva confirmada y pagada. Solo el huésped o un administrador pueden cancelarla; los anfitriones no pueden
            cancelar reservas.
          </span>
        </div>
      );
  }
}
