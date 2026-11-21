import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ArrowLeft, CalendarDays, CircleAlert, LoaderCircle, MapPin, Star, Users } from 'lucide-react';
import { obtenerAlojamiento } from '../../api/alojamientos';
import { listarPeriodos } from '../../api/disponibilidad';
import { mensajeParaUsuario } from '../../api/mensajes';
import { listarResenasAlojamiento } from '../../api/resenas';
import { Estrellas } from '../../components/Estrellas';
import { Miniatura } from '../../components/Miniatura';
import { RUTAS } from '../../routes/rutas';
import type { Alojamiento, PeriodoDisponible } from '../../types/alojamiento';
import type { ResenasAlojamiento } from '../../types/resena';
import { formatearHace, formatearRango, hoyIso } from '../../utils/formato';
import { TarjetaReserva } from './TarjetaReserva';
import './Huesped.css';

/**
 * Detalle de un alojamiento publicado (RF-18, RF-19): fotos, descripción, servicios, reglas,
 * fechas disponibles y reseñas, con la tarjeta para cotizar y reservar (MA-63 a MA-67).
 */
export default function AlojamientoPage() {
  const { id } = useParams();
  const [alojamiento, setAlojamiento] = useState<Alojamiento | null>(null);
  const [periodos, setPeriodos] = useState<PeriodoDisponible[]>([]);
  const [resenas, setResenas] = useState<ResenasAlojamiento | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let activo = true;
    const alojamientoId = Number(id);
    obtenerAlojamiento(alojamientoId)
      .then((a) => {
        if (!activo) return;
        setAlojamiento(a);
        // Lo complementario no impide ver el alojamiento si falla
        listarPeriodos(alojamientoId)
          .then((p) => activo && setPeriodos(p))
          .catch(() => undefined);
        listarResenasAlojamiento(alojamientoId)
          .then((r) => activo && setResenas(r))
          .catch(() => undefined);
      })
      .catch((e: unknown) => activo && setError(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, [id]);

  const volver = (
    <Link to={RUTAS.huesped} className="enlace formulario__volver">
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

  if (!alojamiento) {
    return (
      <div className="cargando">
        <LoaderCircle size={18} className="girando" aria-hidden="true" />
        Cargando alojamiento…
      </div>
    );
  }

  const a = alojamiento;
  const hoy = hoyIso();
  const periodosVigentes = periodos
    .filter((p) => p.fechaFin > hoy)
    .sort((x, y) => x.fechaInicio.localeCompare(y.fechaInicio));

  return (
    <>
      {volver}
      <div className="alojamiento__encabezado">
        <h1>{a.titulo}</h1>
        <p className="alojamiento__resumen">
          {resenas?.promedio != null && (
            <span className="alojamiento__calificacion">
              <Star size={14} aria-hidden="true" />
              {resenas.promedio.toFixed(1)} · {resenas.cantidad} {resenas.cantidad === 1 ? 'reseña' : 'reseñas'}
            </span>
          )}
          <span>
            <MapPin size={14} aria-hidden="true" />
            {a.ciudad.nombre}, {a.ciudad.departamento}
          </span>
        </p>
      </div>

      <Galeria imagenes={a.imagenes} titulo={a.titulo} />

      <div className="alojamiento">
        <div className="alojamiento__principal">
          <section className="alojamiento__seccion">
            <h2>
              {a.tipo.nombre} · anfitrión: {a.anfitrionNombre}
            </h2>
            <p className="alojamiento__datos">
              <Users size={15} aria-hidden="true" />
              Hasta {a.capacidad} {a.capacidad === 1 ? 'huésped' : 'huéspedes'}
            </p>
            <p className="alojamiento__descripcion">{a.descripcion}</p>
          </section>

          {a.servicios.length > 0 && (
            <section className="alojamiento__seccion">
              <h2>Lo que ofrece</h2>
              <ul className="alojamiento__servicios">
                {a.servicios.map((s) => (
                  <li key={s.id}>{s.nombre}</li>
                ))}
              </ul>
            </section>
          )}

          <section className="alojamiento__seccion">
            <h2>Ubicación</h2>
            <p>{a.ubicacion}</p>
          </section>

          {a.reglas.length > 0 && (
            <section className="alojamiento__seccion">
              <h2>Reglas de la casa</h2>
              <ul className="alojamiento__reglas">
                {a.reglas.map((regla) => (
                  <li key={regla}>{regla}</li>
                ))}
              </ul>
            </section>
          )}

          <section className="alojamiento__seccion">
            <h2>Política de cancelación</h2>
            <p>
              {a.politicaCancelacion.nombre}. La devolución depende de cuánta anticipación tengas al cancelar; el valor
              exacto aparece al cancelar desde tu reserva.
            </p>
          </section>

          <section className="alojamiento__seccion">
            <h2>Fechas disponibles</h2>
            {periodosVigentes.length === 0 ? (
              <p className="campo__ayuda">El anfitrión no tiene fechas abiertas por ahora.</p>
            ) : (
              <ul className="alojamiento__periodos">
                {periodosVigentes.map((p) => (
                  <li key={p.id}>
                    <CalendarDays size={14} aria-hidden="true" />
                    {formatearRango(p.fechaInicio < hoy ? hoy : p.fechaInicio, p.fechaFin)}
                  </li>
                ))}
              </ul>
            )}
            <p className="campo__ayuda">Algunos días de estos periodos pueden estar ya reservados.</p>
          </section>

          <section className="alojamiento__seccion">
            <h2>Reseñas</h2>
            {!resenas || resenas.cantidad === 0 ? (
              <p className="campo__ayuda">Este alojamiento todavía no tiene reseñas.</p>
            ) : (
              <ul className="alojamiento__resenas">
                {resenas.resenas.map((r) => (
                  <li key={r.id}>
                    <div className="alojamiento__resena-encabezado">
                      <Estrellas valor={r.calificacion} />
                      <span className="campo__ayuda">{formatearHace(r.creadaEn)}</span>
                    </div>
                    <p>{r.comentario}</p>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>

        <TarjetaReserva alojamiento={a} periodos={periodosVigentes} />
      </div>
    </>
  );
}

/** Foto principal grande y hasta cuatro más pequeñas, como en el mockup. */
function Galeria({ imagenes, titulo }: { imagenes: string[]; titulo: string }) {
  const [principal, ...resto] = imagenes;
  return (
    <div className={resto.length > 0 ? 'galeria' : 'galeria galeria--una'}>
      <Miniatura url={principal} alt={titulo} className="galeria__principal" />
      {resto.slice(0, 4).map((url, i) => (
        <Miniatura key={url} url={url} alt={`${titulo}, foto ${i + 2}`} className="galeria__foto" />
      ))}
    </div>
  );
}
