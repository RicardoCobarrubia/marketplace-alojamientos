import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { CircleAlert, CircleCheck, EyeOff, LoaderCircle, X, Eye } from 'lucide-react';
import { listarAlojamientosAdmin, listarResenasAdmin, mostrarResena, ocultarResena } from '../../api/admin';
import { mensajeParaUsuario } from '../../api/mensajes';
import { Estrellas } from '../../components/Estrellas';
import { FormularioMotivo } from '../../components/FormularioMotivo';
import { rutaDetallePublicacion } from '../../routes/rutas';
import type { Resena } from '../../types/admin';
import { formatearHace } from '../../utils/formato';
import './ModeracionAdmin.css';

type Filtro = 'todas' | 'visibles' | 'ocultas';

const FILTROS: { valor: Filtro; etiqueta: string; oculta?: boolean }[] = [
  { valor: 'todas', etiqueta: 'Todas' },
  { valor: 'visibles', etiqueta: 'Visibles', oculta: false },
  { valor: 'ocultas', etiqueta: 'Ocultas', oculta: true },
];

/**
 * Moderación de reseñas (MA-79, RF-35): ocultar las que tengan lenguaje ofensivo, datos personales,
 * publicidad o contenido ajeno a la estancia, y volver a mostrarlas. Cada acción queda en la auditoría.
 */
export default function ResenasAdminPage() {
  const [filtro, setFiltro] = useState<Filtro>('todas');
  const [resenas, setResenas] = useState<Resena[] | null>(null);
  const [titulos, setTitulos] = useState<Map<number, string>>(new Map());
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [ocultando, setOcultando] = useState<number | null>(null);
  const [mostrando, setMostrando] = useState<number | null>(null);

  useEffect(() => {
    let activo = true;
    listarAlojamientosAdmin()
      .then((lista) => activo && setTitulos(new Map(lista.map((a) => [a.id, a.titulo]))))
      .catch(() => undefined); // los títulos son opcionales: sin ellos se muestra el id
    return () => {
      activo = false;
    };
  }, []);

  useEffect(() => {
    let activo = true;
    setResenas(null);
    setError(null);
    listarResenasAdmin(FILTROS.find((f) => f.valor === filtro)?.oculta)
      .then((lista) => activo && setResenas(lista))
      .catch((e: unknown) => activo && setError(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, [filtro]);

  function reemplazar(actualizada: Resena) {
    setResenas((lista) => {
      if (!lista) return lista;
      // Si ya no cumple el filtro (p. ej. se ocultó estando en "Visibles"), sale de la lista.
      const sigue = filtro === 'todas' || (filtro === 'ocultas') === Boolean(actualizada.oculta);
      return sigue ? lista.map((r) => (r.id === actualizada.id ? actualizada : r)) : lista.filter((r) => r.id !== actualizada.id);
    });
  }

  async function ocultar(resena: Resena, motivo: string) {
    reemplazar(await ocultarResena(resena.id, motivo));
    setOcultando(null);
    setAviso('La reseña quedó oculta: ya no se muestra ni cuenta en el promedio.');
  }

  async function mostrar(resena: Resena) {
    setMostrando(resena.id);
    setError(null);
    setAviso(null);
    try {
      reemplazar(await mostrarResena(resena.id));
      setAviso('La reseña vuelve a ser visible y a contar en el promedio.');
    } catch (e) {
      setError(mensajeParaUsuario(e));
    } finally {
      setMostrando(null);
    }
  }

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Reseñas</h1>
          <p>Calificaciones que los huéspedes dejan después de su estadía</p>
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

      <div className="filtros" role="tablist" aria-label="Filtrar reseñas">
        {FILTROS.map((f) => (
          <button
            key={f.valor}
            type="button"
            role="tab"
            aria-selected={filtro === f.valor}
            className={filtro === f.valor ? 'chip chip--activo' : 'chip'}
            onClick={() => setFiltro(f.valor)}
          >
            {f.etiqueta}
          </button>
        ))}
      </div>

      {!resenas && !error && (
        <div className="cargando">
          <LoaderCircle size={18} className="girando" aria-hidden="true" />
          Cargando reseñas…
        </div>
      )}
      {resenas?.length === 0 && (
        <section className="tarjeta estado-vacio">
          <h2>No hay reseñas {filtro === 'todas' ? 'todavía' : filtro}</h2>
          <p>Los huéspedes pueden reseñar dentro de los 30 días siguientes a una estadía completada.</p>
        </section>
      )}

      <div className="moderacion__lista">
        {resenas?.map((r) => (
          <article key={r.id} className="tarjeta tarjeta--relleno moderacion__item">
            <div className="moderacion__cabecera">
              <div>
                <h2 className="moderacion__titulo">
                  <Estrellas valor={r.calificacion} /> ·{' '}
                  {r.alojamientoId !== null ? (
                    <Link to={rutaDetallePublicacion(r.alojamientoId)} className="enlace">
                      {titulos.get(r.alojamientoId) ?? `Alojamiento #${r.alojamientoId}`}
                    </Link>
                  ) : (
                    'Alojamiento'
                  )}
                </h2>
                <p className="moderacion__detalle">
                  Huésped #{r.huespedId} · Reserva #{r.reservaId} · {formatearHace(r.creadaEn)}
                </p>
              </div>
              <span className={r.oculta ? 'etiqueta-estado etiqueta-estado--bloqueada' : 'etiqueta-estado etiqueta-estado--activa'}>
                {r.oculta ? 'Oculta' : 'Visible'}
              </span>
            </div>

            <p className="moderacion__comentario">{r.comentario}</p>
            {r.oculta && r.motivoOcultamiento && <p className="moderacion__motivo">Motivo: {r.motivoOcultamiento}</p>}

            {ocultando === r.id ? (
              <FormularioMotivo
                etiqueta="Motivo para ocultarla"
                placeholder="Por ejemplo: contiene datos personales del anfitrión."
                ayuda="Queda registrado en la auditoría."
                textoConfirmar="Ocultar reseña"
                textoEnviando="Ocultando…"
                alConfirmar={(motivo) => ocultar(r, motivo)}
                alCancelar={() => setOcultando(null)}
              />
            ) : (
              <div className="moderacion__acciones">
                {r.oculta ? (
                  <button
                    type="button"
                    className="boton boton--secundario boton--pequeno"
                    onClick={() => mostrar(r)}
                    disabled={mostrando !== null}
                  >
                    {mostrando === r.id ? <LoaderCircle size={14} className="girando" aria-hidden="true" /> : <Eye size={14} aria-hidden="true" />}
                    Volver a mostrar
                  </button>
                ) : (
                  <button type="button" className="boton boton--peligro boton--pequeno" onClick={() => setOcultando(r.id)}>
                    <EyeOff size={14} aria-hidden="true" />
                    Ocultar
                  </button>
                )}
              </div>
            )}
          </article>
        ))}
      </div>
    </>
  );
}
