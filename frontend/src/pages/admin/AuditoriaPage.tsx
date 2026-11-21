import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { CircleAlert, LoaderCircle, Search, X } from 'lucide-react';
import { consultarAuditoria } from '../../api/admin';
import { mensajeParaUsuario } from '../../api/mensajes';
import { CampoSeleccion } from '../../components/CampoSeleccion';
import { CampoTexto } from '../../components/CampoTexto';
import type { FiltroAuditoria, RegistroAuditoria } from '../../types/admin';
import './AuditoriaPage.css';

type Modo = FiltroAuditoria['tipo'];

const MODOS: { valor: Modo; etiqueta: string }[] = [
  { valor: 'ninguno', etiqueta: 'Los más recientes' },
  { valor: 'entidad', etiqueta: 'Por entidad' },
  { valor: 'actor', etiqueta: 'Por actor (usuario)' },
  { valor: 'accion', etiqueta: 'Por acción' },
];

const ENTIDADES = [
  'ALOJAMIENTO',
  'RESERVA',
  'USUARIO',
  'RESENA',
  'CIUDAD',
  'TIPO_ALOJAMIENTO',
  'SERVICIO',
  'CONFIGURACION_COTIZACION',
];

const FECHA_HORA = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' });

/** Nombres con tildes que no se pueden deducir del código. */
const NOMBRES: Record<string, string> = {
  RESENA: 'Reseña',
  TIPO_ALOJAMIENTO: 'Tipo de alojamiento',
  CONFIGURACION_COTIZACION: 'Configuración de cotización',
  HUESPED: 'Huésped',
  ANFITRION: 'Anfitrión',
  EXITO: 'Éxito',
};

/** "CAMBIAR_ESTADO_RESERVA" → "Cambiar estado reserva" */
function legible(codigo: string | null): string {
  if (!codigo) return '—';
  if (NOMBRES[codigo]) return NOMBRES[codigo];
  const texto = codigo.toLowerCase().replace(/_/g, ' ');
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

/**
 * Registro de auditoría (MA-87, RF-43 a RF-45): los 200 eventos más recientes. El backend acepta
 * un solo filtro a la vez: entidad (tipo e id), actor o acción.
 */
export default function AuditoriaPage() {
  const [modo, setModo] = useState<Modo>('ninguno');
  const [entidad, setEntidad] = useState(ENTIDADES[0]);
  const [entidadId, setEntidadId] = useState('');
  const [actorId, setActorId] = useState('');
  const [accion, setAccion] = useState('');
  const [filtro, setFiltro] = useState<FiltroAuditoria>({ tipo: 'ninguno' });
  const [registros, setRegistros] = useState<RegistroAuditoria[] | null>(null);
  const [accionesConocidas, setAccionesConocidas] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [errorFiltro, setErrorFiltro] = useState<string | undefined>();

  useEffect(() => {
    let activo = true;
    setRegistros(null);
    setError(null);
    consultarAuditoria(filtro)
      .then((lista) => {
        if (!activo) return;
        setRegistros(lista);
        // Las acciones vistas alimentan la lista de sugerencias del filtro por acción.
        setAccionesConocidas((previas) => [...new Set([...previas, ...lista.map((r) => r.accion)])].sort());
      })
      .catch((e: unknown) => activo && setError(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, [filtro]);

  function aplicar(evento?: FormEvent) {
    evento?.preventDefault();
    setErrorFiltro(undefined);
    if (modo === 'ninguno') return setFiltro({ tipo: 'ninguno' });
    if (modo === 'entidad') {
      const id = Number(entidadId);
      if (!Number.isInteger(id) || id <= 0) return setErrorFiltro('Escribe el id de la entidad (un número).');
      return setFiltro({ tipo: 'entidad', entidad, entidadId: id });
    }
    if (modo === 'actor') {
      const id = Number(actorId);
      if (!Number.isInteger(id) || id <= 0) return setErrorFiltro('Escribe el id del usuario (un número).');
      return setFiltro({ tipo: 'actor', actorId: id });
    }
    const limpia = accion.trim().toUpperCase();
    if (!limpia) return setErrorFiltro('Escribe o elige una acción.');
    setFiltro({ tipo: 'accion', accion: limpia });
  }

  function filtrarPor(nuevo: FiltroAuditoria) {
    setModo(nuevo.tipo);
    if (nuevo.tipo === 'entidad') {
      setEntidad(nuevo.entidad);
      setEntidadId(String(nuevo.entidadId));
    } else if (nuevo.tipo === 'actor') {
      setActorId(String(nuevo.actorId));
    } else if (nuevo.tipo === 'accion') {
      setAccion(nuevo.accion);
    }
    setFiltro(nuevo);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  const descripcionFiltro = useMemo(() => {
    switch (filtro.tipo) {
      case 'entidad':
        return `${legible(filtro.entidad)} #${filtro.entidadId}`;
      case 'actor':
        return `usuario #${filtro.actorId}`;
      case 'accion':
        return legible(filtro.accion);
      default:
        return null;
    }
  }, [filtro]);

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Auditoría</h1>
          <p>Los 200 eventos más recientes del sistema</p>
        </div>
      </div>

      <form className="tarjeta tarjeta--relleno auditoria__filtros" onSubmit={aplicar} noValidate>
        <CampoSeleccion
          etiqueta="Filtrar"
          opciones={MODOS.map((m) => ({ valor: m.valor, etiqueta: m.etiqueta }))}
          value={modo}
          onChange={(e) => {
            setModo(e.target.value as Modo);
            setErrorFiltro(undefined);
          }}
        />
        {modo === 'entidad' && (
          <>
            <CampoSeleccion
              etiqueta="Tipo de entidad"
              opciones={ENTIDADES.map((e) => ({ valor: e, etiqueta: legible(e) }))}
              value={entidad}
              onChange={(e) => setEntidad(e.target.value)}
            />
            <CampoTexto
              etiqueta="Id"
              type="number"
              min={1}
              inputMode="numeric"
              placeholder="12"
              value={entidadId}
              onChange={(e) => setEntidadId(e.target.value)}
              error={errorFiltro}
            />
          </>
        )}
        {modo === 'actor' && (
          <CampoTexto
            etiqueta="Id del usuario"
            type="number"
            min={1}
            inputMode="numeric"
            placeholder="4"
            value={actorId}
            onChange={(e) => setActorId(e.target.value)}
            error={errorFiltro}
          />
        )}
        {modo === 'accion' && (
          <>
            <CampoTexto
              etiqueta="Acción"
              placeholder="BLOQUEAR_ALOJAMIENTO"
              list="acciones-auditoria"
              value={accion}
              onChange={(e) => setAccion(e.target.value.toUpperCase())}
              error={errorFiltro}
            />
            <datalist id="acciones-auditoria">
              {accionesConocidas.map((a) => (
                <option key={a} value={a}>
                  {legible(a)}
                </option>
              ))}
            </datalist>
          </>
        )}
        <button type="submit" className="boton boton--primario auditoria__buscar">
          <Search size={15} aria-hidden="true" />
          Buscar
        </button>
      </form>

      {descripcionFiltro && (
        <p className="auditoria__filtro-activo">
          Mostrando eventos de <strong>{descripcionFiltro}</strong>
          <button
            type="button"
            className="enlace-accion"
            onClick={() => {
              setModo('ninguno');
              setFiltro({ tipo: 'ninguno' });
            }}
          >
            <X size={13} aria-hidden="true" /> Quitar filtro
          </button>
        </p>
      )}

      {error && (
        <div className="alerta alerta--error" role="alert">
          <CircleAlert size={16} aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}

      <section className="tarjeta auditoria__tabla-contenedor">
        {!registros && !error && (
          <div className="cargando">
            <LoaderCircle size={18} className="girando" aria-hidden="true" />
            Cargando eventos…
          </div>
        )}
        {registros?.length === 0 && <div className="estado-vacio">No hay eventos con ese filtro.</div>}
        {registros && registros.length > 0 && (
          <table className="auditoria__tabla">
            <thead>
              <tr>
                <th scope="col">Fecha</th>
                <th scope="col">Actor</th>
                <th scope="col">Acción</th>
                <th scope="col">Entidad</th>
                <th scope="col">Cambio de estado</th>
                <th scope="col">Resultado</th>
              </tr>
            </thead>
            <tbody>
              {registros.map((r) => (
                <tr key={r.id} title={r.correlationId ? `Id de correlación: ${r.correlationId}` : undefined}>
                  <td className="auditoria__fecha">{FECHA_HORA.format(new Date(r.ocurridoEn))}</td>
                  <td>
                    {r.actorId !== null ? (
                      <button type="button" className="enlace-accion" onClick={() => filtrarPor({ tipo: 'actor', actorId: r.actorId! })}>
                        #{r.actorId}
                      </button>
                    ) : (
                      'Sistema'
                    )}
                    {r.actorRol && <span className="auditoria__rol">{legible(r.actorRol)}</span>}
                  </td>
                  <td>
                    <button type="button" className="enlace-accion" onClick={() => filtrarPor({ tipo: 'accion', accion: r.accion })}>
                      {legible(r.accion)}
                    </button>
                    {r.detalle && <span className="auditoria__detalle">{r.detalle}</span>}
                  </td>
                  <td>
                    {r.entidad && r.entidadId !== null ? (
                      <button
                        type="button"
                        className="enlace-accion"
                        onClick={() => filtrarPor({ tipo: 'entidad', entidad: r.entidad!, entidadId: r.entidadId! })}
                      >
                        {legible(r.entidad)} #{r.entidadId}
                      </button>
                    ) : (
                      legible(r.entidad)
                    )}
                  </td>
                  <td className="auditoria__cambio">
                    {r.estadoAnterior || r.estadoNuevo ? `${r.estadoAnterior ?? '—'} → ${r.estadoNuevo ?? '—'}` : '—'}
                  </td>
                  <td>
                    <span
                      className={
                        r.resultado === 'EXITO'
                          ? 'etiqueta-estado etiqueta-estado--activa'
                          : 'etiqueta-estado etiqueta-estado--bloqueada'
                      }
                    >
                      {legible(r.resultado)}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}
