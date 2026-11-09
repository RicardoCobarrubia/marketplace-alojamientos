import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { CalendarSearch, CircleAlert, CircleCheck, LoaderCircle, Pencil, Plus, TriangleAlert, X } from 'lucide-react';
import { listarMisAlojamientos } from '../../../api/alojamientos';
import {
  consultarDisponibilidad,
  crearPeriodo,
  editarPeriodo,
  eliminarPeriodo,
  listarPeriodos,
} from '../../../api/disponibilidad';
import { mensajeParaUsuario } from '../../../api/mensajes';
import { CampoSeleccion } from '../../../components/CampoSeleccion';
import { CampoTexto } from '../../../components/CampoTexto';
import { RUTAS } from '../../../routes/rutas';
import type { Alojamiento, Disponibilidad, MotivoNoDisponible, PeriodoDisponible } from '../../../types/alojamiento';
import { formatearRango, hoyIso } from '../../../utils/formato';
import { Calendario } from './Calendario';
import './DisponibilidadPage.css';

const MOTIVOS: Record<MotivoNoDisponible, string> = {
  NO_ACTIVO: 'el alojamiento no está activo, así que los huéspedes no pueden reservarlo.',
  FUERA_DE_PERIODO: 'esas fechas no están completas dentro de un periodo disponible.',
  OCUPADO: 'ya hay una reserva en esas fechas.',
};

function primerDiaDelMes(fecha = new Date()): Date {
  return new Date(fecha.getFullYear(), fecha.getMonth(), 1);
}

export default function DisponibilidadPage() {
  const [parametros, setParametros] = useSearchParams();
  const [alojamientos, setAlojamientos] = useState<Alojamiento[] | null>(null);
  const [periodos, setPeriodos] = useState<PeriodoDisponible[] | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [mes, setMes] = useState(primerDiaDelMes);

  const [editandoId, setEditandoId] = useState<number | null>(null);
  const [inicio, setInicio] = useState('');
  const [fin, setFin] = useState('');
  const [erroresFechas, setErroresFechas] = useState<{ inicio?: string; fin?: string }>({});
  const [guardando, setGuardando] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const idParametro = Number(parametros.get('alojamiento'));
  const seleccionado = alojamientos?.find((a) => a.id === idParametro) ?? alojamientos?.[0] ?? null;

  useEffect(() => {
    let activo = true;
    listarMisAlojamientos()
      .then((lista) => activo && setAlojamientos(lista))
      .catch((e: unknown) => activo && setErrorCarga(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, []);

  const idSeleccionado = seleccionado?.id ?? null;
  useEffect(() => {
    if (idSeleccionado === null) return;
    let activo = true;
    setPeriodos(null);
    listarPeriodos(idSeleccionado)
      .then((lista) => activo && setPeriodos(lista))
      .catch((e: unknown) => activo && setError(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, [idSeleccionado]);

  function limpiarFormulario() {
    setEditandoId(null);
    setInicio('');
    setFin('');
    setErroresFechas({});
  }

  function cambiarAlojamiento(id: string) {
    limpiarFormulario();
    setAviso(null);
    setError(null);
    setParametros({ alojamiento: id }, { replace: true });
  }

  function empezarEdicion(periodo: PeriodoDisponible) {
    setEditandoId(periodo.id);
    setInicio(periodo.fechaInicio);
    setFin(periodo.fechaFin);
    setErroresFechas({});
    setAviso(null);
    setError(null);
  }

  async function guardar(evento: FormEvent) {
    evento.preventDefault();
    if (!seleccionado || guardando) return;

    const hoy = hoyIso();
    const nuevosErrores: { inicio?: string; fin?: string } = {};
    if (!inicio) nuevosErrores.inicio = 'La fecha de inicio es obligatoria.';
    else if (editandoId === null && inicio < hoy) nuevosErrores.inicio = 'El periodo no puede empezar antes de hoy.';
    if (!fin) nuevosErrores.fin = 'La fecha de fin es obligatoria.';
    else if (inicio && fin <= inicio) nuevosErrores.fin = 'La fecha de fin debe ser posterior a la de inicio.';
    else if (fin <= hoy) nuevosErrores.fin = 'El periodo debe terminar después de hoy.';
    setErroresFechas(nuevosErrores);
    if (nuevosErrores.inicio || nuevosErrores.fin) return;

    setGuardando(true);
    setAviso(null);
    setError(null);
    try {
      const datos = { fechaInicio: inicio, fechaFin: fin };
      if (editandoId === null) {
        const creado = await crearPeriodo(seleccionado.id, datos);
        setPeriodos((lista) => [...(lista ?? []), creado]);
        setAviso('Periodo agregado.');
      } else {
        const editado = await editarPeriodo(seleccionado.id, editandoId, datos);
        setPeriodos((lista) => lista?.map((p) => (p.id === editado.id ? editado : p)) ?? null);
        setAviso('Periodo actualizado.');
      }
      limpiarFormulario();
    } catch (e) {
      setError(mensajeParaUsuario(e));
    } finally {
      setGuardando(false);
    }
  }

  async function eliminar(periodo: PeriodoDisponible) {
    if (!seleccionado) return;
    const confirmado = window.confirm(
      `¿Eliminar el periodo ${formatearRango(periodo.fechaInicio, periodo.fechaFin)}? Las fechas dejarán de estar disponibles.`,
    );
    if (!confirmado) return;
    setAviso(null);
    setError(null);
    try {
      await eliminarPeriodo(seleccionado.id, periodo.id);
      setPeriodos((lista) => lista?.filter((p) => p.id !== periodo.id) ?? null);
      if (editandoId === periodo.id) limpiarFormulario();
      setAviso('Periodo eliminado.');
    } catch (e) {
      setError(mensajeParaUsuario(e));
    }
  }

  if (errorCarga) {
    return (
      <div className="alerta alerta--error" role="alert">
        <CircleAlert size={16} aria-hidden="true" />
        <span>{errorCarga}</span>
      </div>
    );
  }

  if (!alojamientos) {
    return (
      <div className="cargando">
        <LoaderCircle size={18} className="girando" aria-hidden="true" />
        Cargando…
      </div>
    );
  }

  if (!seleccionado) {
    return (
      <>
        <div className="encabezado-pagina">
          <div>
            <h1>Disponibilidad</h1>
            <p>Define las fechas en que tus alojamientos se pueden reservar</p>
          </div>
        </div>
        <section className="tarjeta estado-vacio">
          <h2>Primero registra un alojamiento</h2>
          <p>La disponibilidad se define para cada alojamiento.</p>
          <Link to={RUTAS.nuevoAlojamiento} className="boton boton--primario">
            <Plus size={16} aria-hidden="true" />
            Nuevo alojamiento
          </Link>
        </section>
      </>
    );
  }

  const ordenados = [...(periodos ?? [])].sort((a, b) => a.fechaInicio.localeCompare(b.fechaInicio));

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Disponibilidad</h1>
          <p>{seleccionado.titulo}</p>
        </div>
        <div className="disponibilidad__selector">
          <CampoSeleccion
            etiqueta="Alojamiento"
            opciones={alojamientos.map((a) => ({ valor: a.id, etiqueta: a.titulo }))}
            value={seleccionado.id}
            onChange={(e) => cambiarAlojamiento(e.target.value)}
          />
        </div>
      </div>

      {aviso && (
        <div className="alerta alerta--exito" role="status">
          <CircleCheck size={16} aria-hidden="true" />
          <span>{aviso}</span>
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

      <div className="disponibilidad__cuerpo">
        <Calendario mes={mes} periodos={periodos ?? []} alCambiarMes={setMes} />

        <div className="disponibilidad__lateral">
          <section className="tarjeta tarjeta--relleno">
            <h2 className="disponibilidad__subtitulo">Periodos disponibles</h2>
            {!periodos && (
              <div className="cargando cargando--compacto">
                <LoaderCircle size={16} className="girando" aria-hidden="true" />
              </div>
            )}
            {periodos?.length === 0 && (
              <p className="campo__ayuda">Todavía no hay periodos. Agrega uno para que los huéspedes puedan reservar.</p>
            )}
            {ordenados.length > 0 && (
              <ul className="periodos">
                {ordenados.map((p) => (
                  <li key={p.id} className={editandoId === p.id ? 'periodo periodo--editando' : 'periodo'}>
                    <button
                      type="button"
                      className="periodo__fechas"
                      onClick={() => setMes(primerDiaDelMes(new Date(`${p.fechaInicio}T00:00`)))}
                      title="Ver en el calendario"
                    >
                      {formatearRango(p.fechaInicio, p.fechaFin)}
                    </button>
                    <button
                      type="button"
                      className="adicionales__boton"
                      onClick={() => empezarEdicion(p)}
                      aria-label={`Editar periodo ${formatearRango(p.fechaInicio, p.fechaFin)}`}
                    >
                      <Pencil size={14} />
                    </button>
                    <button
                      type="button"
                      className="adicionales__boton"
                      onClick={() => eliminar(p)}
                      aria-label={`Eliminar periodo ${formatearRango(p.fechaInicio, p.fechaFin)}`}
                    >
                      <X size={15} />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="tarjeta tarjeta--relleno">
            <h2 className="disponibilidad__subtitulo">{editandoId === null ? 'Nuevo periodo' : 'Editar periodo'}</h2>
            <form onSubmit={guardar} noValidate>
              <div className="formulario__fila">
                <CampoTexto
                  etiqueta="Inicio"
                  type="date"
                  min={editandoId === null ? hoyIso() : undefined}
                  value={inicio}
                  onChange={(e) => setInicio(e.target.value)}
                  error={erroresFechas.inicio}
                  disabled={guardando}
                />
                <CampoTexto
                  etiqueta="Fin"
                  type="date"
                  min={inicio || hoyIso()}
                  value={fin}
                  onChange={(e) => setFin(e.target.value)}
                  error={erroresFechas.fin}
                  disabled={guardando}
                />
              </div>
              <p className="campo__ayuda disponibilidad__nota">El fin es el último día en que un huésped puede salir.</p>
              <div className="alerta alerta--aviso">
                <TriangleAlert size={16} aria-hidden="true" />
                <span>No puedes eliminar ni reducir periodos que tengan reservas confirmadas o en revisión.</span>
              </div>
              <div className="disponibilidad__botones">
                {editandoId !== null && (
                  <button type="button" className="boton boton--secundario" onClick={limpiarFormulario} disabled={guardando}>
                    Cancelar
                  </button>
                )}
                <button type="submit" className="boton boton--primario boton--bloque" disabled={guardando}>
                  {guardando && <LoaderCircle size={16} className="girando" aria-hidden="true" />}
                  {editandoId === null ? 'Agregar periodo' : 'Guardar cambios'}
                </button>
              </div>
            </form>
          </section>

          <ConsultaFechas alojamientoId={seleccionado.id} />
        </div>
      </div>
    </>
  );
}

/** Consulta si se puede reservar en unas fechas, igual que lo verá un huésped (MA-59). */
function ConsultaFechas({ alojamientoId }: { alojamientoId: number }) {
  const [llegada, setLlegada] = useState('');
  const [salida, setSalida] = useState('');
  const [resultado, setResultado] = useState<Disponibilidad | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [consultando, setConsultando] = useState(false);

  useEffect(() => {
    setResultado(null);
    setError(null);
  }, [alojamientoId]);

  async function consultar(evento: FormEvent) {
    evento.preventDefault();
    setResultado(null);
    setError(null);
    if (!llegada || !salida) return setError('Elige la fecha de llegada y la de salida.');
    if (salida <= llegada) return setError('La salida debe ser posterior a la llegada.');
    setConsultando(true);
    try {
      setResultado(await consultarDisponibilidad(alojamientoId, llegada, salida));
    } catch (e) {
      setError(mensajeParaUsuario(e));
    } finally {
      setConsultando(false);
    }
  }

  return (
    <section className="tarjeta tarjeta--relleno">
      <h2 className="disponibilidad__subtitulo">Consultar fechas</h2>
      <form onSubmit={consultar} noValidate>
        <div className="formulario__fila">
          <CampoTexto etiqueta="Llegada" type="date" min={hoyIso()} value={llegada} onChange={(e) => setLlegada(e.target.value)} />
          <CampoTexto
            etiqueta="Salida"
            type="date"
            min={llegada || hoyIso()}
            value={salida}
            onChange={(e) => setSalida(e.target.value)}
          />
        </div>
        <button type="submit" className="boton boton--secundario boton--bloque" disabled={consultando}>
          {consultando ? (
            <LoaderCircle size={16} className="girando" aria-hidden="true" />
          ) : (
            <CalendarSearch size={15} aria-hidden="true" />
          )}
          Consultar disponibilidad
        </button>
      </form>
      {error && <p className="campo__error">{error}</p>}
      {resultado && (
        <div
          className={resultado.disponible ? 'alerta alerta--exito consulta__resultado' : 'alerta alerta--aviso consulta__resultado'}
          role="status"
        >
          {resultado.disponible ? <CircleCheck size={16} aria-hidden="true" /> : <TriangleAlert size={16} aria-hidden="true" />}
          <span>
            {resultado.disponible
              ? `Disponible: ${resultado.noches} ${resultado.noches === 1 ? 'noche' : 'noches'}.`
              : `No disponible: ${resultado.motivo ? MOTIVOS[resultado.motivo] : 'las fechas no se pueden reservar.'}`}
          </span>
        </div>
      )}
    </section>
  );
}
