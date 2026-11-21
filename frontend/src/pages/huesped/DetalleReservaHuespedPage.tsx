import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { ArrowLeft, CircleAlert, CircleCheck, Info, LoaderCircle, RotateCw, TriangleAlert } from 'lucide-react';
import { obtenerAlojamiento } from '../../api/alojamientos';
import { mensajeParaUsuario } from '../../api/mensajes';
import { cancelarReserva, obtenerReserva } from '../../api/reservas';
import { DesgloseReserva } from '../../components/DesgloseReserva';
import { EtiquetaReserva } from '../../components/EtiquetaReserva';
import { FormularioMotivo } from '../../components/FormularioMotivo';
import { Miniatura } from '../../components/Miniatura';
import { RUTAS, rutaAlojamientoHuesped, rutaReservaHuesped } from '../../routes/rutas';
import type { Alojamiento } from '../../types/alojamiento';
import type { Reserva } from '../../types/reserva';
import { formatearFecha, formatearHace, formatearPrecio } from '../../utils/formato';
import { limiteParaResenar, sePuedeCancelar, sePuedeResenar } from '../../utils/reservas';
import { FormularioResena } from './FormularioResena';
import '../anfitrion/ReservasRecibidasPage.css';
import './Huesped.css';

const FECHA_HORA = new Intl.DateTimeFormat('es-CO', { dateStyle: 'long', timeStyle: 'short' });

/** Lo que llega en el state de la navegación después de reservar o cancelar. */
interface AvisoNavegacion {
  recienReservada?: boolean;
}

/**
 * Detalle de una reserva del huésped (MA-70): desglose del pago, cancelación con motivo (MA-72,
 * MA-73) y reseña de la estadía completada (MA-77).
 */
export default function DetalleReservaHuespedPage() {
  const { id } = useParams();
  const { state } = useLocation() as { state: AvisoNavegacion | null };
  const [reserva, setReserva] = useState<Reserva | null>(null);
  const [alojamiento, setAlojamiento] = useState<Alojamiento | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [recargas, setRecargas] = useState(0);
  const [recienCancelada, setRecienCancelada] = useState(false);

  useEffect(() => {
    let activo = true;
    obtenerReserva(Number(id))
      .then((r) => {
        if (!activo) return;
        setReserva(r);
        // Si el alojamiento ya no está publicado el backend responde 404: se muestra sin él
        obtenerAlojamiento(r.alojamientoId)
          .then((a) => activo && setAlojamiento(a))
          .catch(() => undefined);
      })
      .catch((e: unknown) => activo && setError(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, [id, recargas]);

  const volver = (
    <Link to={RUTAS.misReservas} className="enlace formulario__volver">
      <ArrowLeft size={14} aria-hidden="true" />
      Mis reservas
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

      {state?.recienReservada && r.estado === 'CONFIRMADA' && (
        <div className="alerta alerta--exito" role="status">
          <CircleCheck size={16} aria-hidden="true" />
          <span>¡Listo! Tu reserva quedó confirmada y el pago fue aprobado.</span>
        </div>
      )}

      {recienCancelada && (
        <div className="alerta alerta--exito" role="status">
          <CircleCheck size={16} aria-hidden="true" />
          <span>
            Cancelaste la reserva.{' '}
            {r.valorDevolucion ? `Te devolveremos ${formatearPrecio(r.valorDevolucion)}.` : 'Según la política no hay devolución.'}
          </span>
        </div>
      )}

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
                    <Link
                      to={rutaAlojamientoHuesped(alojamiento.id)}
                      state={{ volver: { ruta: rutaReservaHuesped(r.id), etiqueta: `Reserva #${r.id}` } }}
                      className="enlace"
                    >
                      Ver alojamiento
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
              {alojamiento && (
                <div>
                  <dt>Anfitrión</dt>
                  <dd>{alojamiento.anfitrionNombre}</dd>
                </div>
              )}
              <div>
                <dt>Reservada el</dt>
                <dd>{FECHA_HORA.format(new Date(r.creadoEn))}</dd>
              </div>
            </dl>
          </section>

          <EstadoDeLaReserva
            reserva={r}
            alojamiento={alojamiento}
            alActualizar={() => setRecargas((n) => n + 1)}
            alCancelar={(cancelada) => {
              setReserva(cancelada);
              setRecienCancelada(true);
            }}
          />
        </div>

        <aside className="tarjeta tarjeta--relleno">
          <h2 className="tarjeta__titulo">Desglose del pago</h2>
          <DesgloseReserva reserva={r} etiquetaTotal="Total pagado" />
          <p className="campo__ayuda detalle-reserva__nota">
            Son los precios del momento de la reserva: no cambian aunque el anfitrión edite el alojamiento.
          </p>
        </aside>
      </div>
    </>
  );
}

interface PropiedadesEstado {
  reserva: Reserva;
  alojamiento: Alojamiento | null;
  alActualizar: () => void;
  alCancelar: (cancelada: Reserva) => void;
}

/** Lo que el huésped puede hacer o saber según el estado de la reserva (RF-27 a RF-33). */
function EstadoDeLaReserva({ reserva: r, alojamiento, alActualizar, alCancelar }: PropiedadesEstado) {
  const [cancelando, setCancelando] = useState(false);

  switch (r.estado) {
    case 'CONFIRMADA':
    case 'EN_REVISION': {
      const enRevision = r.estado === 'EN_REVISION' && (
        <div className="alerta alerta--aviso" role="status">
          <TriangleAlert size={16} aria-hidden="true" />
          <span>
            Un administrador está revisando esta reserva por una medida de moderación. Si decide cancelarla, te
            devolverá el 100 % del pago.
          </span>
        </div>
      );
      if (!sePuedeCancelar(r)) {
        return (
          <>
            {enRevision}
            <div className="alerta alerta--info" role="status">
              <Info size={16} aria-hidden="true" />
              <span>Tu estadía ya empezó, así que la reserva no se puede cancelar. ¡Disfruta tu estadía!</span>
            </div>
          </>
        );
      }
      return (
        <>
          {enRevision}
          <section className="tarjeta tarjeta--relleno">
            <h2 className="tarjeta__titulo">Cancelar la reserva</h2>
            <p className="campo__ayuda detalle-huesped__texto">
              Puedes cancelar hasta el día antes de la llegada. La devolución depende de la política de cancelación
              {alojamiento ? ` (${alojamiento.politicaCancelacion.nombre.toLowerCase()})` : ''} y de cuánta
              anticipación tengas: el valor exacto lo calcula el sistema al confirmar. Las fechas quedan libres para
              otros huéspedes.
            </p>
            {cancelando ? (
              <FormularioMotivo
                etiqueta="Motivo de la cancelación"
                placeholder="Por ejemplo: cambié mis planes de viaje"
                ayuda="El anfitrión verá este motivo."
                textoConfirmar="Cancelar reserva"
                textoEnviando="Cancelando…"
                textoDescartar="Volver"
                alConfirmar={async (motivo) => alCancelar(await cancelarReserva(r.id, motivo))}
                alCancelar={() => setCancelando(false)}
              />
            ) : (
              <button type="button" className="boton boton--secundario" onClick={() => setCancelando(true)}>
                Cancelar reserva
              </button>
            )}
          </section>
        </>
      );
    }
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
              <dt>Devolución</dt>
              <dd>
                {r.valorDevolucion !== null ? formatearPrecio(r.valorDevolucion) : '—'}
                {r.devolucionPendiente && ' (en trámite)'}
              </dd>
            </div>
          </dl>
          {r.motivoCancelacion && <p className="moderacion__motivo">Motivo: {r.motivoCancelacion}</p>}
          {r.devolucionPendiente && (
            <p className="campo__ayuda detalle-reserva__nota">
              La pasarela de pagos está procesando la devolución; puede tardar unos minutos en reflejarse.
            </p>
          )}
        </section>
      );
    case 'COMPLETADA':
      if (sePuedeResenar(r)) return <FormularioResena reservaId={r.id} limite={limiteParaResenar(r)} />;
      return (
        <div className="alerta alerta--info" role="status">
          <Info size={16} aria-hidden="true" />
          <span>
            Tu estadía terminó. El plazo para dejar una reseña venció el {formatearFecha(limiteParaResenar(r))}.
          </span>
        </div>
      );
    case 'PAGO_FALLIDO':
      return (
        <div className="alerta alerta--error" role="status">
          <CircleAlert size={16} aria-hidden="true" />
          <span>
            El pago no se completó{r.motivoFallo ? `: ${r.motivoFallo}` : ''}. No se hizo ningún cobro y las fechas
            quedaron libres; puedes intentar reservar de nuevo.
          </span>
        </div>
      );
    case 'PENDIENTE_PAGO':
      return (
        <div className="alerta alerta--info detalle-huesped__pendiente" role="status">
          <Info size={16} aria-hidden="true" />
          <span>Estamos confirmando tu pago con la pasarela. Las fechas quedan apartadas mientras tanto.</span>
          <button type="button" className="boton boton--secundario boton--pequeno" onClick={alActualizar}>
            <RotateCw size={14} aria-hidden="true" />
            Actualizar
          </button>
        </div>
      );
  }
}
