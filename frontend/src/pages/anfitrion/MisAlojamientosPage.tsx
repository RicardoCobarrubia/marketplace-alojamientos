import { useEffect, useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router';
import { CircleAlert, CircleCheck, LoaderCircle, Plus, X } from 'lucide-react';
import {
  enviarARevision,
  listarMisAlojamientos,
  pausarAlojamiento,
  reanudarAlojamiento,
} from '../../api/alojamientos';
import { ApiError } from '../../api/ApiError';
import { mensajeParaUsuario } from '../../api/mensajes';
import { EtiquetaEstado } from '../../components/EtiquetaEstado';
import { Miniatura } from '../../components/Miniatura';
import { RUTAS, rutaDisponibilidad, rutaEditarAlojamiento } from '../../routes/rutas';
import { NOMBRE_ESTADO, type Alojamiento, type EstadoAlojamiento } from '../../types/alojamiento';
import { formatearPrecio } from '../../utils/formato';
import { listarCamposFaltantes } from './textos';
import './MisAlojamientosPage.css';

type Filtro = EstadoAlojamiento | 'TODOS';
type Accion = 'enviar' | 'pausar' | 'reanudar';

const FILTROS: Filtro[] = ['TODOS', 'BORRADOR', 'PENDIENTE_REVISION', 'ACTIVA', 'PAUSADA', 'BLOQUEADA'];

const ACCIONES: Record<Accion, { llamar: (id: number) => Promise<Alojamiento>; aviso: string }> = {
  enviar: { llamar: enviarARevision, aviso: 'se envió a revisión. Un administrador lo revisará.' },
  pausar: { llamar: pausarAlojamiento, aviso: 'quedó pausado y ya no aparece en las búsquedas.' },
  reanudar: { llamar: reanudarAlojamiento, aviso: 'está activo de nuevo.' },
};

interface EstadoNavegacion {
  aviso?: string;
}

export default function MisAlojamientosPage() {
  const ubicacion = useLocation();
  const [alojamientos, setAlojamientos] = useState<Alojamiento[] | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [filtro, setFiltro] = useState<Filtro>('TODOS');
  const [enCurso, setEnCurso] = useState<number | null>(null);
  const [aviso, setAviso] = useState<string | null>(
    () => (ubicacion.state as EstadoNavegacion | null)?.aviso ?? null,
  );
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let activo = true;
    listarMisAlojamientos()
      .then((lista) => activo && setAlojamientos(lista))
      .catch((e: unknown) => activo && setErrorCarga(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, []);

  const conteo = useMemo(() => {
    const resultado: Partial<Record<Filtro, number>> = { TODOS: alojamientos?.length ?? 0 };
    alojamientos?.forEach((a) => (resultado[a.estado] = (resultado[a.estado] ?? 0) + 1));
    return resultado;
  }, [alojamientos]);

  const visibles = alojamientos?.filter((a) => filtro === 'TODOS' || a.estado === filtro) ?? [];

  async function ejecutar(alojamiento: Alojamiento, accion: Accion) {
    setEnCurso(alojamiento.id);
    setAviso(null);
    setError(null);
    try {
      const actualizado = await ACCIONES[accion].llamar(alojamiento.id);
      setAlojamientos((lista) => lista?.map((a) => (a.id === actualizado.id ? actualizado : a)) ?? null);
      setAviso(`«${actualizado.titulo}» ${ACCIONES[accion].aviso}`);
    } catch (e) {
      if (e instanceof ApiError && e.codigo === 'INFORMACION_INCOMPLETA') {
        setError(`A «${alojamiento.titulo}» le falta: ${listarCamposFaltantes(e.campos)}. Edítalo y vuelve a enviarlo.`);
      } else {
        setError(mensajeParaUsuario(e));
      }
    } finally {
      setEnCurso(null);
    }
  }

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Mis alojamientos</h1>
          <p>Administra tus publicaciones y su estado</p>
        </div>
        <Link to={RUTAS.nuevoAlojamiento} className="boton boton--primario">
          <Plus size={16} aria-hidden="true" />
          Nuevo alojamiento
        </Link>
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
          <button type="button" className="alerta__cerrar" onClick={() => setError(null)} aria-label="Cerrar error">
            <X size={16} />
          </button>
        </div>
      )}

      {alojamientos && alojamientos.length > 0 && (
        <div className="filtros" role="group" aria-label="Filtrar por estado">
          {FILTROS.map((f) => (
            <button
              key={f}
              type="button"
              className={filtro === f ? 'chip chip--activo' : 'chip'}
              aria-pressed={filtro === f}
              onClick={() => setFiltro(f)}
            >
              {f === 'TODOS' ? 'Todos' : NOMBRE_ESTADO[f]}
              <span className="chip__contador">{conteo[f] ?? 0}</span>
            </button>
          ))}
        </div>
      )}

      <section className="tarjeta">
        {errorCarga && (
          <div className="estado-vacio">
            <p>{errorCarga}</p>
          </div>
        )}

        {!alojamientos && !errorCarga && (
          <div className="cargando">
            <LoaderCircle size={18} className="girando" aria-hidden="true" />
            Cargando tus alojamientos…
          </div>
        )}

        {alojamientos?.length === 0 && (
          <div className="estado-vacio">
            <h2>Aún no tienes alojamientos</h2>
            <p>Registra tu primer alojamiento. Se guarda como borrador hasta que lo envíes a revisión.</p>
            <Link to={RUTAS.nuevoAlojamiento} className="boton boton--primario">
              <Plus size={16} aria-hidden="true" />
              Nuevo alojamiento
            </Link>
          </div>
        )}

        {alojamientos && alojamientos.length > 0 && (
          <table className="tabla-alojamientos">
            <thead>
              <tr>
                <th scope="col">Alojamiento</th>
                <th scope="col">Estado</th>
                <th scope="col">Precio / noche</th>
                <th scope="col">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {visibles.map((a) => (
                <tr key={a.id}>
                  <td data-titulo="Alojamiento">
                    <div className="tabla-alojamientos__alojamiento">
                      <Miniatura url={a.imagenes[0]} alt={a.titulo} />
                      <div>
                        <div className="tabla-alojamientos__titulo">{a.titulo}</div>
                        <div className="tabla-alojamientos__ciudad">
                          {a.ciudad.nombre}, {a.ciudad.departamento}
                        </div>
                      </div>
                    </div>
                  </td>
                  <td data-titulo="Estado">
                    <EtiquetaEstado estado={a.estado} />
                    {a.estado === 'BORRADOR' && a.motivoRechazo && (
                      <div className="tabla-alojamientos__motivo">Rechazado: {a.motivoRechazo}</div>
                    )}
                  </td>
                  <td data-titulo="Precio / noche">{formatearPrecio(a.precioNoche)}</td>
                  <td data-titulo="Acciones">
                    <AccionesAlojamiento
                      alojamiento={a}
                      ocupado={enCurso === a.id}
                      deshabilitado={enCurso !== null}
                      alEjecutar={(accion) => ejecutar(a, accion)}
                    />
                  </td>
                </tr>
              ))}
              {visibles.length === 0 && (
                <tr>
                  <td colSpan={4} className="tabla-alojamientos__sin-resultados">
                    No tienes alojamientos en estado «{filtro === 'TODOS' ? '' : NOMBRE_ESTADO[filtro]}».
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

interface PropiedadesAcciones {
  alojamiento: Alojamiento;
  ocupado: boolean;
  deshabilitado: boolean;
  alEjecutar: (accion: Accion) => void;
}

/** Acciones que permite cada estado (RF-09, RF-10). */
function AccionesAlojamiento({ alojamiento, ocupado, deshabilitado, alEjecutar }: PropiedadesAcciones) {
  if (ocupado) {
    return (
      <span className="acciones acciones--texto">
        <LoaderCircle size={14} className="girando" aria-hidden="true" /> Actualizando…
      </span>
    );
  }

  const editar = (
    <Link to={rutaEditarAlojamiento(alojamiento.id)} className="enlace-accion">
      {alojamiento.motivoRechazo && alojamiento.estado === 'BORRADOR' ? 'Corregir' : 'Editar'}
    </Link>
  );
  const disponibilidad = (
    <Link to={rutaDisponibilidad(alojamiento.id)} className="enlace-accion">
      Disponibilidad
    </Link>
  );

  switch (alojamiento.estado) {
    case 'BORRADOR':
      return (
        <span className="acciones">
          {editar}
          <button
            type="button"
            className="enlace-accion enlace-accion--destacada"
            disabled={deshabilitado}
            onClick={() => alEjecutar('enviar')}
          >
            {alojamiento.motivoRechazo ? 'Reenviar a revisión' : 'Enviar a revisión'}
          </button>
          {disponibilidad}
        </span>
      );
    case 'PENDIENTE_REVISION':
      return <span className="acciones acciones--texto">En revisión por un administrador</span>;
    case 'ACTIVA':
      return (
        <span className="acciones">
          {disponibilidad}
          <button
            type="button"
            className="enlace-accion enlace-accion--destacada"
            disabled={deshabilitado}
            onClick={() => alEjecutar('pausar')}
          >
            Pausar
          </button>
          {editar}
        </span>
      );
    case 'PAUSADA':
      return (
        <span className="acciones">
          <button
            type="button"
            className="enlace-accion enlace-accion--destacada"
            disabled={deshabilitado}
            onClick={() => alEjecutar('reanudar')}
          >
            Reanudar
          </button>
          {editar}
          {disponibilidad}
        </span>
      );
    case 'BLOQUEADA':
      return <span className="acciones acciones--texto">Solo un administrador puede editarlo</span>;
  }
}
