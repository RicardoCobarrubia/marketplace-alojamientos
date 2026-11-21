import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { CircleAlert, LoaderCircle, Star, Users } from 'lucide-react';
import { mensajeParaUsuario } from '../../api/mensajes';
import { listarReservas } from '../../api/reservas';
import { EtiquetaReserva } from '../../components/EtiquetaReserva';
import { Miniatura } from '../../components/Miniatura';
import { useAlojamientosDeReservas } from '../../hooks/useAlojamientosDeReservas';
import { rutaReservaHuesped } from '../../routes/rutas';
import type { Reserva } from '../../types/reserva';
import { formatearFecha, formatearPrecio, formatearRango, hoyIso } from '../../utils/formato';
import { ordenarReservas, sePuedeResenar } from '../../utils/reservas';
import '../anfitrion/MisAlojamientosPage.css';
import '../anfitrion/ReservasRecibidasPage.css';

type Pestana = 'PROXIMAS' | 'ANTERIORES';

/** Próximas: las que todavía no terminan y siguen en pie (pagadas, en revisión o pagándose). */
function esProxima(r: Reserva, hoy: string): boolean {
  return r.salida >= hoy && (r.estado === 'CONFIRMADA' || r.estado === 'EN_REVISION' || r.estado === 'PENDIENTE_PAGO');
}

/** Reservas del huésped (MA-70, RF-26): próximas y anteriores, con acceso al detalle. */
export default function MisReservasPage() {
  const [reservas, setReservas] = useState<Reserva[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pestana, setPestana] = useState<Pestana>('PROXIMAS');
  const alojamientos = useAlojamientosDeReservas(reservas);

  useEffect(() => {
    let activo = true;
    listarReservas()
      .then((lista) => activo && setReservas(ordenarReservas(lista)))
      .catch((e: unknown) => activo && setError(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, []);

  const hoy = hoyIso();
  const { proximas, anteriores } = useMemo(() => {
    const lista = reservas ?? [];
    return {
      proximas: lista.filter((r) => esProxima(r, hoy)),
      // Las anteriores (pasadas, canceladas o fallidas), de la más reciente a la más antigua
      anteriores: lista.filter((r) => !esProxima(r, hoy)).sort((x, y) => y.llegada.localeCompare(x.llegada)),
    };
  }, [reservas, hoy]);
  const visibles = pestana === 'PROXIMAS' ? proximas : anteriores;
  const porResenar = anteriores.filter(sePuedeResenar).length;
  const siguiente = proximas.find((r) => r.estado === 'CONFIRMADA');

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Mis reservas</h1>
          <p>Tus próximas estadías y las anteriores</p>
        </div>
      </div>

      {error && (
        <div className="alerta alerta--error" role="alert">
          <CircleAlert size={16} aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}

      {siguiente && (
        <div className="alerta alerta--info reservas__proxima" role="status">
          <span>
            Tu próxima estadía:{' '}
            <strong>{alojamientos.get(siguiente.alojamientoId)?.titulo ?? `Alojamiento #${siguiente.alojamientoId}`}</strong>{' '}
            desde el {formatearFecha(siguiente.llegada)}
          </span>
        </div>
      )}

      {porResenar > 0 && (
        <div className="alerta alerta--exito" role="status">
          <Star size={16} aria-hidden="true" />
          <span>
            Tienes {porResenar === 1 ? 'una estadía' : `${porResenar} estadías`} por reseñar. Abre el detalle para
            calificarla.
          </span>
        </div>
      )}

      {reservas && reservas.length > 0 && (
        <div className="filtros" role="group" aria-label="Mostrar reservas">
          {(
            [
              ['PROXIMAS', 'Próximas', proximas.length],
              ['ANTERIORES', 'Anteriores', anteriores.length],
            ] as const
          ).map(([valor, etiqueta, cantidad]) => (
            <button
              key={valor}
              type="button"
              className={pestana === valor ? 'chip chip--activo' : 'chip'}
              aria-pressed={pestana === valor}
              onClick={() => setPestana(valor)}
            >
              {etiqueta}
              <span className="chip__contador">{cantidad}</span>
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
            <p>Cuando reserves un alojamiento, aquí verás las fechas, el pago y el estado de tu estadía.</p>
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
                          <div className="tabla-alojamientos__ciudad">
                            {alojamiento ? `${alojamiento.ciudad.nombre} · ` : ''}Reserva #{r.id}
                          </div>
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
                      <Link to={rutaReservaHuesped(r.id)} className="boton boton--secundario boton--pequeno">
                        {sePuedeResenar(r) ? 'Reseñar' : 'Ver detalle'}
                      </Link>
                    </td>
                  </tr>
                );
              })}
              {visibles.length === 0 && (
                <tr>
                  <td colSpan={6} className="tabla-alojamientos__sin-resultados">
                    {pestana === 'PROXIMAS' ? 'No tienes estadías próximas.' : 'Todavía no tienes reservas anteriores.'}
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
