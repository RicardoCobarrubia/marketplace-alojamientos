import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ArrowLeft, Check, CircleAlert, CircleCheck, Circle, LoaderCircle, Send } from 'lucide-react';
import {
  agregarServicioAdicional,
  crearAlojamiento,
  editarAlojamiento,
  editarServicioAdicional,
  enviarARevision,
  obtenerAlojamiento,
  retirarServicioAdicional,
} from '../../../api/alojamientos';
import { ApiError } from '../../../api/ApiError';
import { mensajeParaUsuario } from '../../../api/mensajes';
import { useCatalogos } from '../../../hooks/useCatalogos';
import { RUTAS } from '../../../routes/rutas';
import type { Alojamiento, EstadoAlojamiento, ServicioAdicional } from '../../../types/alojamiento';
import { leerNumero } from '../../../utils/formato';
import { listarCamposFaltantes } from '../textos';
import {
  aDatos,
  camposDesdeBackend,
  desdeAlojamiento,
  FORMULARIO_VACIO,
  PASOS,
  primerPasoConError,
  validarPaso,
  validarTodo,
  type AdicionalLocal,
  type CampoFormulario,
  type Errores,
  type Formulario,
} from './modelo';
import { PasoImagenes } from './PasoImagenes';
import { PasoInformacion } from './PasoInformacion';
import { PasoPrecios } from './PasoPrecios';
import { PasoResumen } from './PasoResumen';
import type { PropiedadesPaso } from './tipos';
import './FormularioAlojamientoPage.css';

const CONSEJOS: string[][] = [
  [
    'Un título claro ayuda a que te encuentren.',
    'Indica la ubicación textual con referencias cercanas.',
    'La capacidad define cuántos huéspedes pueden reservar.',
  ],
  [
    'El precio por noche no incluye la limpieza ni la tarifa de servicio.',
    'Escribe una regla por línea.',
    'Los servicios adicionales son opcionales: el huésped los elige al reservar.',
  ],
  [
    'Pega el enlace de cada imagen. La primera es la principal.',
    'Puedes usar entre 1 y 10 imágenes.',
    'La política define cuánto se devuelve si el huésped cancela.',
  ],
];

/**
 * Sincroniza los servicios adicionales del formulario con los guardados en el backend: retira los
 * que se quitaron, crea los nuevos y edita los que cambiaron (MA-55). Llama `alAvanzar` después de
 * cada creación para no duplicar servicios si algo falla y el usuario reintenta.
 */
async function sincronizarAdicionales(
  alojamientoId: number,
  locales: AdicionalLocal[],
  guardados: ServicioAdicional[],
  alAvanzar: (lista: AdicionalLocal[]) => void,
): Promise<AdicionalLocal[]> {
  const lista = locales.map((item) => ({ ...item }));
  const idsLocales = new Set(lista.map((item) => item.id).filter((id) => id !== undefined));

  for (const guardado of guardados) {
    if (!idsLocales.has(guardado.id)) await retirarServicioAdicional(alojamientoId, guardado.id);
  }
  for (const item of lista) {
    const datos = { nombre: item.nombre.trim(), costo: leerNumero(item.costo), modalidad: item.modalidad };
    if (item.id === undefined) {
      item.id = (await agregarServicioAdicional(alojamientoId, datos)).id;
      alAvanzar(lista);
      continue;
    }
    const original = guardados.find((g) => g.id === item.id);
    const cambio =
      original && (original.nombre !== datos.nombre || original.costo !== datos.costo || original.modalidad !== datos.modalidad);
    if (cambio) await editarServicioAdicional(alojamientoId, item.id, datos);
  }
  return lista;
}

export default function FormularioAlojamientoPage() {
  const { id } = useParams();
  const idRuta = id ? Number(id) : null;
  const navigate = useNavigate();
  const { catalogos, error: errorCatalogos } = useCatalogos();

  const [alojamiento, setAlojamiento] = useState<Alojamiento | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);
  const [formulario, setFormulario] = useState<Formulario>(FORMULARIO_VACIO);
  const [paso, setPaso] = useState(0);
  const [errores, setErrores] = useState<Errores>({});
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [guardando, setGuardando] = useState<'guardar' | 'enviar' | null>(null);

  /** Id del alojamiento ya guardado: después del primer guardado los siguientes son ediciones. */
  const idGuardado = useRef<number | null>(idRuta);
  const adicionalesGuardados = useRef<ServicioAdicional[]>([]);

  useEffect(() => {
    if (idRuta === null) return;
    let activo = true;
    obtenerAlojamiento(idRuta)
      .then((a) => {
        if (!activo) return;
        adicionalesGuardados.current = a.serviciosAdicionales;
        setAlojamiento(a);
        setFormulario(desdeAlojamiento(a));
      })
      .catch((e: unknown) => activo && setErrorCarga(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, [idRuta]);

  const esNuevo = idRuta === null;
  const estado: EstadoAlojamiento = alojamiento?.estado ?? 'BORRADOR';
  const puedeEnviar = estado === 'BORRADOR';
  const deshabilitado = guardando !== null;

  function irAPaso(nuevo: number) {
    setPaso(nuevo);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function cambiar<C extends CampoFormulario>(campo: C, valor: Formulario[C]) {
    setFormulario((f) => ({ ...f, [campo]: valor }));
    setErrores((e) => (e[campo] ? { ...e, [campo]: undefined } : e));
  }

  function continuar() {
    const erroresPaso = validarPaso(paso, formulario);
    setErrores(erroresPaso);
    if (Object.values(erroresPaso).some(Boolean)) return;
    setErrorGeneral(null);
    irAPaso(paso + 1);
  }

  async function guardar(enviar: boolean) {
    const erroresTodos = validarTodo(formulario);
    if (Object.values(erroresTodos).some(Boolean)) {
      setErrores(erroresTodos);
      setErrorGeneral('Revisa los campos marcados antes de guardar.');
      irAPaso(primerPasoConError(erroresTodos));
      return;
    }

    setGuardando(enviar ? 'enviar' : 'guardar');
    setErrorGeneral(null);
    let guardadoAlMenosUnaVez = false;
    try {
      const datos = aDatos(formulario);
      const guardado = idGuardado.current
        ? await editarAlojamiento(idGuardado.current, datos)
        : await crearAlojamiento(datos);
      guardadoAlMenosUnaVez = true;
      idGuardado.current = guardado.id;
      adicionalesGuardados.current = guardado.serviciosAdicionales;

      const adicionales = await sincronizarAdicionales(
        guardado.id,
        formulario.adicionales,
        adicionalesGuardados.current,
        (lista) => setFormulario((f) => ({ ...f, adicionales: [...lista] })),
      );
      setFormulario((f) => ({ ...f, adicionales }));

      if (enviar) await enviarARevision(guardado.id);

      const titulo = `«${guardado.titulo}»`;
      const aviso = enviar
        ? `${titulo} se envió a revisión. Un administrador lo revisará.`
        : esNuevo
          ? `${titulo} se guardó como borrador.`
          : `Se guardaron los cambios de ${titulo}.`;
      navigate(RUTAS.anfitrion, { state: { aviso } });
    } catch (e) {
      const prefijo = guardadoAlMenosUnaVez ? 'El alojamiento quedó guardado como borrador, pero ' : '';
      if (e instanceof ApiError && e.codigo === 'INFORMACION_INCOMPLETA') {
        setErrorGeneral(`${prefijo}no se pudo enviar a revisión: falta ${listarCamposFaltantes(e.campos)}.`);
      } else if (e instanceof ApiError && e.codigo === 'DATOS_INVALIDOS') {
        const campos = camposDesdeBackend(e.errores);
        setErrores(campos);
        const enFormulario = Object.keys(campos).length > 0;
        setErrorGeneral(enFormulario ? 'Revisa los campos marcados.' : `${prefijo}${Object.values(e.errores).join(' ')}`);
        if (enFormulario) irAPaso(primerPasoConError(campos));
      } else {
        setErrorGeneral(prefijo ? `${prefijo}${mensajeParaUsuario(e).toLowerCase()}` : mensajeParaUsuario(e));
      }
      setGuardando(null);
    }
  }

  const titulo = esNuevo ? 'Nuevo alojamiento' : 'Editar alojamiento';

  if (errorCarga || errorCatalogos) {
    return (
      <>
        <EncabezadoFormulario titulo={titulo} subtitulo="" />
        <div className="alerta alerta--error" role="alert">
          <CircleAlert size={16} aria-hidden="true" />
          <span>{errorCarga ?? errorCatalogos}</span>
        </div>
      </>
    );
  }

  if (!catalogos || (!esNuevo && !alojamiento)) {
    return (
      <div className="cargando">
        <LoaderCircle size={18} className="girando" aria-hidden="true" />
        Cargando…
      </div>
    );
  }

  if (estado === 'BLOQUEADA') {
    return (
      <>
        <EncabezadoFormulario titulo={titulo} subtitulo={alojamiento?.titulo ?? ''} />
        <div className="alerta alerta--aviso" role="alert">
          <CircleAlert size={16} aria-hidden="true" />
          <span>Este alojamiento está bloqueado. Solo un administrador puede editarlo.</span>
        </div>
      </>
    );
  }

  const propiedades: PropiedadesPaso = {
    formulario,
    errores,
    catalogos,
    deshabilitado,
    cambiar,
    cambiarAdicionales: (adicionales) => setFormulario((f) => ({ ...f, adicionales })),
  };

  const requisitos = construirRequisitos(formulario);
  const subtitulo =
    paso === 0 && esNuevo ? 'Se guarda como borrador hasta que lo envíes a revisión' : `Paso ${paso + 1} de ${PASOS.length}`;

  return (
    <>
      <EncabezadoFormulario titulo={titulo} subtitulo={subtitulo} />

      <ol className="pasos" aria-label="Pasos del formulario">
        {PASOS.map((nombre, indice) => {
          const clase = indice < paso ? 'paso paso--hecho' : indice === paso ? 'paso paso--actual' : 'paso';
          const contenido = (
            <>
              <span className="paso__circulo" aria-hidden="true">
                {indice < paso ? <Check size={13} /> : indice + 1}
              </span>
              <span className="paso__nombre">{nombre}</span>
            </>
          );
          return (
            <li key={nombre} className={clase} aria-current={indice === paso ? 'step' : undefined}>
              {indice < paso ? (
                <button type="button" className="paso__boton" onClick={() => irAPaso(indice)} disabled={deshabilitado}>
                  {contenido}
                </button>
              ) : (
                <span className="paso__boton">{contenido}</span>
              )}
            </li>
          );
        })}
      </ol>

      {errorGeneral && (
        <div className="alerta alerta--error" role="alert">
          <CircleAlert size={16} aria-hidden="true" />
          <span>{errorGeneral}</span>
        </div>
      )}

      <div className="formulario__cuerpo">
        <section className="tarjeta tarjeta--relleno formulario__principal">
          {paso === 0 && <PasoInformacion {...propiedades} />}
          {paso === 1 && <PasoPrecios {...propiedades} />}
          {paso === 2 && <PasoImagenes {...propiedades} />}
          {paso === 3 && <PasoResumen {...propiedades} estado={estado} />}

          {paso < 3 && (
            <div className="formulario__botones">
              {paso === 0 ? (
                <Link to={RUTAS.anfitrion} className="boton boton--secundario">
                  Cancelar
                </Link>
              ) : (
                <button type="button" className="boton boton--secundario" onClick={() => irAPaso(paso - 1)}>
                  Atrás
                </button>
              )}
              <button type="button" className="boton boton--primario" onClick={continuar}>
                Continuar
              </button>
            </div>
          )}
          {paso === 3 && (
            <div className="formulario__botones">
              <button type="button" className="boton boton--secundario" onClick={() => irAPaso(2)} disabled={deshabilitado}>
                Atrás
              </button>
            </div>
          )}
        </section>

        <aside className="tarjeta tarjeta--relleno formulario__lateral">
          {paso < 3 ? (
            <>
              <h3 className="formulario__lateral-titulo">Consejos</h3>
              <ul className="lista-chequeo">
                {CONSEJOS[paso].map((consejo) => (
                  <li key={consejo}>
                    <Check size={14} aria-hidden="true" />
                    {consejo}
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <>
              <h3 className="formulario__lateral-titulo">Requisitos para publicar</h3>
              <ul className="lista-chequeo">
                {requisitos.map((r) => (
                  <li key={r.texto} className={r.cumple ? 'lista-chequeo__ok' : 'lista-chequeo__falta'}>
                    {r.cumple ? <CircleCheck size={15} aria-hidden="true" /> : <Circle size={15} aria-hidden="true" />}
                    {r.texto}
                    <span className="solo-lectores">{r.cumple ? '(cumple)' : '(falta)'}</span>
                  </li>
                ))}
              </ul>
              <div className="formulario__acciones-finales">
                {puedeEnviar && (
                  <button
                    type="button"
                    className="boton boton--primario boton--bloque"
                    onClick={() => guardar(true)}
                    disabled={deshabilitado}
                  >
                    {guardando === 'enviar' ? (
                      <LoaderCircle size={16} className="girando" aria-hidden="true" />
                    ) : (
                      <Send size={15} aria-hidden="true" />
                    )}
                    {guardando === 'enviar' ? 'Enviando…' : 'Enviar a revisión'}
                  </button>
                )}
                <button
                  type="button"
                  className={puedeEnviar ? 'boton boton--secundario boton--bloque' : 'boton boton--primario boton--bloque'}
                  onClick={() => guardar(false)}
                  disabled={deshabilitado}
                >
                  {guardando === 'guardar' && <LoaderCircle size={16} className="girando" aria-hidden="true" />}
                  {guardando === 'guardar' ? 'Guardando…' : puedeEnviar ? 'Guardar borrador' : 'Guardar cambios'}
                </button>
              </div>
              {!puedeEnviar && (
                <p className="campo__ayuda">
                  {estado === 'PENDIENTE_REVISION'
                    ? 'Está en revisión: los cambios se guardan sin volver a enviarlo.'
                    : 'Los cambios no afectan las reservas ya confirmadas.'}
                </p>
              )}
            </>
          )}
        </aside>
      </div>
    </>
  );
}

function EncabezadoFormulario({ titulo, subtitulo }: { titulo: string; subtitulo: string }) {
  return (
    <div className="encabezado-pagina">
      <div>
        <Link to={RUTAS.anfitrion} className="enlace formulario__volver">
          <ArrowLeft size={14} aria-hidden="true" />
          Mis alojamientos
        </Link>
        <h1>{titulo}</h1>
        {subtitulo && <p>{subtitulo}</p>}
      </div>
    </div>
  );
}

/** Lo que el backend revisa antes de aceptar el envío a revisión (MA-56, escenario 1). */
function construirRequisitos(f: Formulario): { texto: string; cumple: boolean }[] {
  const e = { ...validarPaso(0, f), ...validarPaso(1, f), ...validarPaso(2, f) };
  return [
    { texto: 'Título y descripción', cumple: !e.titulo && !e.descripcion },
    { texto: 'Ubicación, ciudad y tipo', cumple: !e.ubicacion && !e.ciudadId && !e.tipoId },
    { texto: 'Capacidad mayor que cero', cumple: !e.capacidad },
    { texto: 'Precio por noche positivo', cumple: !e.precioNoche && !e.tarifaLimpieza },
    { texto: 'Servicios y reglas', cumple: !e.servicioIds && !e.reglas },
    { texto: 'Al menos una imagen', cumple: !e.imagenes },
    { texto: 'Política de cancelación', cumple: !e.politicaCancelacionId },
  ];
}
