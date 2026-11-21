import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { CircleAlert, LoaderCircle, Users } from 'lucide-react';
import { listarMisAlojamientos } from '../../api/alojamientos';
import { mensajeParaUsuario } from '../../api/mensajes';
import { listarReservas } from '../../api/reservas';
import { EtiquetaReserva } from '../../components/EtiquetaReserva';
import { Miniatura } from '../../components/Miniatura';
import { rutaDetalleReservaAnfitrion } from '../../routes/rutas';
import type { Alojamiento } from '../../types/alojamiento';
import { NOMBRE_ESTADO_RESERVA, type EstadoReserva, type Reserva } from '../../types/reserva';
import { formatearFecha, formatearPrecio, formatearRango, hoyIso } from '../../utils/formato';
import { ordenarReservas } from '../../utils/reservas';
import './MisAlojamientosPage.css';
import './ReservasRecibidasPage.css';

type Filtro = EstadoReserva | 'TODAS';

const FILTROS: Filtro[] = ['TODAS', 'CONFIRMADA', 'EN_REVISION', 'COMPLETADA', 'CANCELADA', 'PAGO_FALLIDO', 'PENDIENTE_PAGO'];

/**
 * Reservas de los alojamientos del anfitrión (MA-70, RF-26). Solo consulta: el anfitrión no
 * puede cancelar reservas (RF-30).
 */
export default function ReservasRecibidasPage() {
  const [reservas, setReservas] = useState<Reserva[] | null>(null);
  const [alojamientos, setAlojamientos] = useState<Map<number, Alojamiento>>(new Map());
  const [error, setError] = useState<string | null>(null);
  const [filtro, setFiltro] = useState<Filtro>('TODAS');

  useEffect(() => {
    let activo = true;
    // ReservaDTO solo trae el id del alojamiento: se cruza con "mis alojamientos" para el título.
    Promise.all([listarReservas(), listarMisAlojamientos()])
      .then(([lista, mios]) => {
        if (!activo) return;
        setReservas(ordenarReservas(lista));
        setAlojamientos(new Map(mios.map((a) => [a.id, a])));
      })
      .catch((e: unknown) => activo && setError(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, []);

  const conteo = useMemo(() => {
    const resultado: Partial<Record<Filtro, number>> = { TODAS: reservas?.length ?? 0 };
    reservas?.forEach((r) => (resultado[r.estado] = (resultado[r.estado] ?? 0) + 1));
    return resultado;
  }, [reservas]);

  const hoy = hoyIso();
  const proximas = reservas?.filter((r) => r.estado === 'CONFIRMADA' && r.llegada >= hoy) ?? [];
  const visibles = reservas?.filter((r) => filtro === 'TODAS' || r.estado === filtro) ?? [];
  // Solo se muestran los filtros de estados que existen, más "Todas".
  const filtrosConDatos = FILTROS.filter((f) => f === 'TODAS' || (conteo[f] ?? 0) > 0);

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Reservas recibidas</h1>
          <p>Las reservas de tus alojamientos</p>
        </div>
      </div>

      {error && (
        <div className="alerta alerta--error" role="alert">
          <CircleAlert size={16} aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}

      {proximas.length > 0 && (
        <div className="alerta alerta--info reservas__proxima" role="status">
          <span>
            Próxima llegada: <strong>{alojamientos.get(proximas[0].alojamientoId)?.titulo ?? `Alojamiento #${proximas[0].alojamientoId}`}</strong>{' '}
            el {formatearFecha(proximas[0].llegada)} · {proximas[0].huespedes} huéspedes
            {proximas.length > 1 && ` · ${proximas.length - 1} más después`}
          </span>
        </div>
      )}

      {reservas && reservas.length > 0 && (
        <div className="filtros" role="group" aria-label="Filtrar por estado">
          {filtrosConDatos.map((f) => (
            <button
              key={f}
              type="button"
              className={filtro === f ? 'chip chip--activo' : 'chip'}
              aria-pressed={filtro === f}
              onClick={() => setFiltro(f)}
            >
              {f === 'TODAS' ? 'Todas' : NOMBRE_ESTADO_RESERVA[f]}
              <span className="chip__contador">{conteo[f] ?? 0}</span>
            </button>
          ))}
        </div>
      )}

      <section className="tarjeta">
        {!reservas && !error && (
          <div className="cargando">
            <LoaderCircle size={18} className="girando" aria-hidden="true" />
            Cargando reservas…
          </div>
        )}

        {reservas?.length === 0 && (
          <div className="estado-vacio">
            <h2>Todavía no tienes reservas</h2>
            <p>Cuando un huésped reserve uno de tus alojamientos activos, aparecerá aquí.</p>
          </div>
        )}

        {reservas && reservas.length > 0 && (
          <table className="tabla-alojamientos">
            <thead>
              <tr>
                <th scope="col">Alojamiento</th>
                <th scope="col">Fechas</th>
                <th scope="col">Huéspedes</th>
                <th scope="col">Total</th>
                <th scope="col">Estado</th>
                <th scope="col">
                  <span className="solo-lectores">Acciones</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {visibles.map((r) => {
                const alojamiento = alojamientos.get(r.alojamientoId);
                return (
                  <tr key={r.id}>
                    <td data-titulo="Alojamiento">
                      <div className="tabla-alojamientos__alojamiento">
                        <Miniatura url={alojamiento?.imagenes[0]} alt={alojamiento?.titulo ?? ''} />
                        <div>
                          <div className="tabla-alojamientos__titulo">
                            {alojamiento?.titulo ?? `Alojamiento #${r.alojamientoId}`}
                          </div>
                          <div className="tabla-alojamientos__ciudad">Reserva #{r.id}</div>
                        </div>
                      </div>
                    </td>
                    <td data-titulo="Fechas">
                      {formatearRango(r.llegada, r.salida)}
                      <div className="tabla-alojamientos__ciudad">
                        {r.noches} {r.noches === 1 ? 'noche' : 'noches'}
                      </div>
                    </td>
                    <td data-titulo="Huéspedes">
                      <span className="reservas__huespedes">
                        <Users size={14} aria-hidden="true" /> {r.huespedes}
                      </span>
                    </td>
                    <td data-titulo="Total">{formatearPrecio(r.total)}</td>
                    <td data-titulo="Estado">
                      <EtiquetaReserva estado={r.estado} />
                    </td>
                    <td data-titulo="Acciones">
                      <Link to={rutaDetalleReservaAnfitrion(r.id)} className="boton boton--secundario boton--pequeno">
                        Ver detalle
                      </Link>
                    </td>
                  </tr>
                );
              })}
              {visibles.length === 0 && (
                <tr>
                  <td colSpan={6} className="tabla-alojamientos__sin-resultados">
                    No hay reservas en ese estado.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}
