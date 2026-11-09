import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ArrowLeft, Check, CircleAlert, LoaderCircle, MapPin, Users, X } from 'lucide-react';
import { aprobarAlojamiento, rechazarAlojamiento } from '../../api/admin';
import { obtenerAlojamiento } from '../../api/alojamientos';
import { ApiError } from '../../api/ApiError';
import { mensajeParaUsuario } from '../../api/mensajes';
import { CampoAreaTexto } from '../../components/CampoAreaTexto';
import { EtiquetaEstado } from '../../components/EtiquetaEstado';
import { Miniatura } from '../../components/Miniatura';
import { RUTAS } from '../../routes/rutas';
import { NOMBRE_MODALIDAD, type Alojamiento } from '../../types/alojamiento';
import { aIso, formatearFecha, formatearHace, formatearPrecio } from '../../utils/formato';
import './DetallePublicacionPage.css';

const MAXIMO_MOTIVO = 500;

/** Detalle de una publicación para que el administrador la apruebe o la rechace (MA-57, RF-12). */
export default function DetallePublicacionPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [alojamiento, setAlojamiento] = useState<Alojamiento | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [imagen, setImagen] = useState(0);

  const [rechazando, setRechazando] = useState(false);
  const [motivo, setMotivo] = useState('');
  const [errorMotivo, setErrorMotivo] = useState<string | undefined>();
  const [enviando, setEnviando] = useState<'aprobar' | 'rechazar' | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let activo = true;
    obtenerAlojamiento(Number(id))
      .then((a) => activo && setAlojamiento(a))
      .catch((e: unknown) => activo && setErrorCarga(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, [id]);

  async function decidir(accion: 'aprobar' | 'rechazar') {
    if (!alojamiento || enviando) return;
    if (accion === 'rechazar') {
      const limpio = motivo.trim();
      if (!limpio) return setErrorMotivo('Escribe el motivo: el anfitrión lo verá para corregir la publicación.');
      if (limpio.length > MAXIMO_MOTIVO) return setErrorMotivo(`El motivo admite máximo ${MAXIMO_MOTIVO} caracteres.`);
    }
    setEnviando(accion);
    setError(null);
    try {
      const resultado =
        accion === 'aprobar'
          ? await aprobarAlojamiento(alojamiento.id)
          : await rechazarAlojamiento(alojamiento.id, motivo.trim());
      const aviso =
        accion === 'aprobar'
          ? `«${resultado.titulo}» quedó activo y ya aparece en las búsquedas.`
          : `«${resultado.titulo}» se devolvió al anfitrión con el motivo del rechazo.`;
      navigate(RUTAS.adminPublicaciones, { state: { aviso } });
    } catch (e) {
      if (e instanceof ApiError && e.codigo === 'NO_PENDIENTE') {
        setError('Esta publicación ya no está pendiente: otro administrador la revisó. Recargamos su estado.');
        obtenerAlojamiento(alojamiento.id).then(setAlojamiento).catch(() => undefined);
      } else if (e instanceof ApiError && e.codigo === 'DATOS_INVALIDOS') {
        setErrorMotivo(e.errores.motivoRechazo ?? e.message);
      } else {
        setError(mensajeParaUsuario(e));
      }
      setEnviando(null);
    }
  }

  const volver = (
    <Link to={RUTAS.adminPublicaciones} className="enlace formulario__volver">
      <ArrowLeft size={14} aria-hidden="true" />
      Revisión de publicaciones
    </Link>
  );

  if (errorCarga) {
    return (
      <>
        {volver}
        <div className="alerta alerta--error" role="alert">
          <CircleAlert size={16} aria-hidden="true" />
          <span>{errorCarga}</span>
        </div>
      </>
    );
  }

  if (!alojamiento) {
    return (
      <div className="cargando">
        <LoaderCircle size={18} className="girando" aria-hidden="true" />
        Cargando publicación…
      </div>
    );
  }

  const a = alojamiento;
  const pendiente = a.estado === 'PENDIENTE_REVISION';

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          {volver}
          <h1>{a.titulo}</h1>
          <p className="detalle__subtitulo">
            <MapPin size={14} aria-hidden="true" /> {a.ubicacion} · {a.ciudad.nombre}, {a.ciudad.departamento}
          </p>
        </div>
      </div>

      {error && (
        <div className="alerta alerta--error" role="alert">
          <CircleAlert size={16} aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}

      <div className="detalle__cuerpo">
        <div className="detalle__principal">
          <section className="tarjeta tarjeta--relleno">
            <Miniatura url={a.imagenes[imagen]} alt={`Imagen ${imagen + 1} de ${a.titulo}`} className="detalle__imagen" />
            {a.imagenes.length > 1 && (
              <div className="detalle__miniaturas" role="group" aria-label="Imágenes">
                {a.imagenes.map((url, indice) => (
                  <button
                    key={url}
                    type="button"
                    className={indice === imagen ? 'detalle__miniatura detalle__miniatura--activa' : 'detalle__miniatura'}
                    onClick={() => setImagen(indice)}
                    aria-label={`Ver imagen ${indice + 1}`}
                    aria-pressed={indice === imagen}
                  >
                    <Miniatura url={url} alt="" />
                  </button>
                ))}
              </div>
            )}
          </section>

          <section className="tarjeta tarjeta--relleno">
            <h2 className="tarjeta__titulo">Descripción</h2>
            <p className="detalle__texto">{a.descripcion}</p>

            <dl className="detalle__datos">
              <div>
                <dt>Tipo</dt>
                <dd>{a.tipo.nombre}</dd>
              </div>
              <div>
                <dt>Capacidad</dt>
                <dd>
                  <Users size={14} aria-hidden="true" /> {a.capacidad} huéspedes
                </dd>
              </div>
              <div>
                <dt>Precio por noche</dt>
                <dd>{formatearPrecio(a.precioNoche)}</dd>
              </div>
              <div>
                <dt>Tarifa de limpieza</dt>
                <dd>{formatearPrecio(a.tarifaLimpieza)}</dd>
              </div>
              <div>
                <dt>Política de cancelación</dt>
                <dd>{a.politicaCancelacion.nombre}</dd>
              </div>
            </dl>
          </section>

          <section className="tarjeta tarjeta--relleno">
            <h2 className="tarjeta__titulo">Servicios y reglas</h2>
            <div className="formulario__chips">
              {a.servicios.map((s) => (
                <span key={s.id} className="chip detalle__chip">
                  {s.nombre}
                </span>
              ))}
            </div>
            <ul className="detalle__reglas">
              {a.reglas.map((regla) => (
                <li key={regla}>{regla}</li>
              ))}
            </ul>

            {a.serviciosAdicionales.length > 0 && (
              <>
                <h3 className="detalle__subtitulo-seccion">Servicios adicionales</h3>
                <ul className="detalle__adicionales">
                  {a.serviciosAdicionales.map((s) => (
                    <li key={s.id}>
                      <span>{s.nombre}</span>
                      <span>
                        {formatearPrecio(s.costo)} · {NOMBRE_MODALIDAD[s.modalidad].toLowerCase()}
                      </span>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </section>
        </div>

        <aside className="tarjeta tarjeta--relleno detalle__decision">
          <div className="detalle__estado">
            <EtiquetaEstado estado={a.estado} />
            <span className="campo__ayuda">Actualizado {formatearHace(a.actualizadoEn)}</span>
          </div>
          <dl className="detalle__anfitrion">
            <div>
              <dt>Anfitrión</dt>
              <dd>{a.anfitrionNombre}</dd>
            </div>
            <div>
              <dt>Registrado</dt>
              <dd>{formatearFecha(aIso(new Date(a.creadoEn)))}</dd>
            </div>
          </dl>

          {pendiente && !rechazando && (
            <div className="detalle__botones">
              <button
                type="button"
                className="boton boton--primario boton--bloque"
                onClick={() => decidir('aprobar')}
                disabled={enviando !== null}
              >
                {enviando === 'aprobar' ? (
                  <LoaderCircle size={16} className="girando" aria-hidden="true" />
                ) : (
                  <Check size={16} aria-hidden="true" />
                )}
                {enviando === 'aprobar' ? 'Aprobando…' : 'Aprobar y publicar'}
              </button>
              <button
                type="button"
                className="boton boton--peligro boton--bloque"
                onClick={() => setRechazando(true)}
                disabled={enviando !== null}
              >
                <X size={16} aria-hidden="true" />
                Rechazar
              </button>
            </div>
          )}

          {pendiente && rechazando && (
            <div className="detalle__rechazo">
              <CampoAreaTexto
                etiqueta="Motivo del rechazo"
                placeholder="Por ejemplo: agrega fotos del exterior y aclara la ubicación."
                rows={4}
                maxLength={MAXIMO_MOTIVO}
                value={motivo}
                onChange={(e) => {
                  setMotivo(e.target.value);
                  setErrorMotivo(undefined);
                }}
                error={errorMotivo}
                ayuda={`El anfitrión lo verá en su lista. ${motivo.length}/${MAXIMO_MOTIVO}`}
                disabled={enviando !== null}
                autoFocus
              />
              <div className="detalle__botones">
                <button
                  type="button"
                  className="boton boton--peligro boton--bloque"
                  onClick={() => decidir('rechazar')}
                  disabled={enviando !== null}
                >
                  {enviando === 'rechazar' && <LoaderCircle size={16} className="girando" aria-hidden="true" />}
                  {enviando === 'rechazar' ? 'Rechazando…' : 'Confirmar rechazo'}
                </button>
                <button
                  type="button"
                  className="boton boton--secundario boton--bloque"
                  onClick={() => {
                    setRechazando(false);
                    setErrorMotivo(undefined);
                  }}
                  disabled={enviando !== null}
                >
                  Cancelar
                </button>
              </div>
            </div>
          )}

          {!pendiente && (
            <p className="campo__ayuda detalle__nota">
              Solo las publicaciones en revisión se pueden aprobar o rechazar.
              {a.estado === 'BORRADOR' && a.motivoRechazo && (
                <>
                  <br />
                  <strong>Último motivo de rechazo:</strong> {a.motivoRechazo}
                </>
              )}
            </p>
          )}
        </aside>
      </div>
    </>
  );
}
