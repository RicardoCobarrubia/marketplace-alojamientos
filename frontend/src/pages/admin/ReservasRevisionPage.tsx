import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { CalendarDays, CircleAlert, CircleCheck, LoaderCircle, Users, X } from 'lucide-react';
import { cancelarReservaAdmin, listarAlojamientosAdmin, listarReservasEnRevision, mantenerReserva } from '../../api/admin';
import { mensajeParaUsuario } from '../../api/mensajes';
import { EtiquetaEstado } from '../../components/EtiquetaEstado';
import { FormularioMotivo } from '../../components/FormularioMotivo';
import { rutaDetallePublicacion } from '../../routes/rutas';
import type { Alojamiento } from '../../types/alojamiento';
import type { Reserva } from '../../types/reserva';
import { formatearFecha, formatearHace, formatearPrecio } from '../../utils/formato';
import './ModeracionAdmin.css';

/**
 * Reservas que quedaron EN_REVISION porque se bloqueó su alojamiento o se restringió a alguien
 * (MA-82, RF-38). El administrador decide mantenerlas o cancelarlas con devolución del 100 %.
 */
export default function ReservasRevisionPage() {
  const [reservas, setReservas] = useState<Reserva[] | null>(null);
  const [alojamientos, setAlojamientos] = useState<Map<number, Alojamiento>>(new Map());
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [cancelando, setCancelando] = useState<number | null>(null);
  const [manteniendo, setManteniendo] = useState<number | null>(null);

  useEffect(() => {
    let activo = true;
    // ReservaDTO solo trae el id del alojamiento: se cruza con la lista para mostrar su título.
    Promise.all([listarReservasEnRevision(), listarAlojamientosAdmin()])
      .then(([lista, todos]) => {
        if (!activo) return;
        setReservas(lista);
        setAlojamientos(new Map(todos.map((a) => [a.id, a])));
      })
      .catch((e: unknown) => activo && setError(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, []);

  function quitar(id: number) {
    setReservas((lista) => lista?.filter((r) => r.id !== id) ?? null);
  }

  async function mantener(reserva: Reserva) {
    setManteniendo(reserva.id);
    setError(null);
    setAviso(null);
    try {
      await mantenerReserva(reserva.id);
      quitar(reserva.id);
      setAviso(`La reserva #${reserva.id} se mantuvo y volvió a estar confirmada.`);
    } catch (e) {
      setError(mensajeParaUsuario(e));
    } finally {
      setManteniendo(null);
    }
  }

  async function cancelar(reserva: Reserva, motivo: string) {
    const resultado = await cancelarReservaAdmin(reserva.id, motivo);
    quitar(reserva.id);
    setCancelando(null);
    const devolucion = resultado.valorDevolucion ?? reserva.total;
    setAviso(`La reserva #${reserva.id} se canceló con devolución de ${formatearPrecio(devolucion)}.`);
  }

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Reservas en revisión</h1>
          <p>Reservas afectadas por el bloqueo de un alojamiento o la restricción de un usuario</p>
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

      {!reservas && !error && (
        <div className="cargando">
          <LoaderCircle size={18} className="girando" aria-hidden="true" />
          Cargando reservas…
        </div>
      )}

      {reservas?.length === 0 && (
        <section className="tarjeta estado-vacio">
          <h2>No hay reservas en revisión</h2>
          <p>Aparecen aquí cuando se bloquea un alojamiento o se restringe a un usuario con reservas futuras.</p>
        </section>
      )}

      <div className="moderacion__lista">
        {reservas?.map((r) => {
          const alojamiento = alojamientos.get(r.alojamientoId);
          return (
            <article key={r.id} className="tarjeta tarjeta--relleno moderacion__item">
              <div className="moderacion__cabecera">
                <div>
                  <h2 className="moderacion__titulo">
                    Reserva #{r.id} ·{' '}
                    <Link to={rutaDetallePublicacion(r.alojamientoId)} className="enlace">
                      {alojamiento?.titulo ?? `Alojamiento #${r.alojamientoId}`}
                    </Link>
                  </h2>
                  <p className="moderacion__detalle">
                    <CalendarDays size={14} aria-hidden="true" /> {formatearFecha(r.llegada)} – {formatearFecha(r.salida)} ·{' '}
                    {r.noches} {r.noches === 1 ? 'noche' : 'noches'} · <Users size={14} aria-hidden="true" /> {r.huespedes}{' '}
                    huéspedes · Huésped #{r.huespedId}
                  </p>
                </div>
                <div className="moderacion__total">
                  <span>{formatearPrecio(r.total)}</span>
                  <span className="campo__ayuda">Reservada {formatearHace(r.creadoEn)}</span>
                </div>
              </div>

              {alojamiento && (
                <p className="moderacion__contexto">
                  Alojamiento <EtiquetaEstado estado={alojamiento.estado} /> · Anfitrión: {alojamiento.anfitrionNombre}
                </p>
              )}

              {cancelando === r.id ? (
                <FormularioMotivo
                  etiqueta="Motivo de la cancelación"
                  placeholder="Por ejemplo: el alojamiento fue bloqueado por información engañosa."
                  ayuda="El huésped recibe la devolución del 100 %."
                  textoConfirmar="Cancelar con devolución"
                  textoEnviando="Cancelando…"
                  alConfirmar={(motivo) => cancelar(r, motivo)}
                  alCancelar={() => setCancelando(null)}
                />
              ) : (
                <div className="moderacion__acciones">
                  <button
                    type="button"
                    className="boton boton--primario boton--pequeno"
                    onClick={() => mantener(r)}
                    disabled={manteniendo !== null}
                  >
                    {manteniendo === r.id && <LoaderCircle size={14} className="girando" aria-hidden="true" />}
                    Mantener reserva
                  </button>
                  <button
                    type="button"
                    className="boton boton--peligro boton--pequeno"
                    onClick={() => setCancelando(r.id)}
                    disabled={manteniendo !== null}
                  >
                    Cancelar con devolución del 100 %
                  </button>
                </div>
              )}
            </article>
          );
        })}
      </div>
    </>
  );
}
