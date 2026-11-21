import { useState, type FormEvent } from 'react';
import { CircleAlert, CircleCheck, Info, LoaderCircle, Star } from 'lucide-react';
import { ApiError } from '../../api/ApiError';
import { mensajeParaUsuario } from '../../api/mensajes';
import { publicarResena } from '../../api/resenas';
import { CampoAreaTexto } from '../../components/CampoAreaTexto';
import { Estrellas } from '../../components/Estrellas';
import type { Resena } from '../../types/admin';
import { LARGO_MAXIMO_COMENTARIO, LARGO_MINIMO_COMENTARIO } from '../../types/resena';
import { formatearFecha } from '../../utils/formato';

const TEXTO_CALIFICACION = ['', 'Muy mala', 'Mala', 'Regular', 'Buena', 'Excelente'];

interface PropiedadesFormularioResena {
  reservaId: number;
  /** Último día para reseñar (ISO). */
  limite: string;
}

/** Calificación de 1 a 5 y comentario de una estadía completada (MA-77, RF-33). */
export function FormularioResena({ reservaId, limite }: PropiedadesFormularioResena) {
  const [calificacion, setCalificacion] = useState(0);
  const [resaltada, setResaltada] = useState(0);
  const [comentario, setComentario] = useState('');
  const [errores, setErrores] = useState<{ calificacion?: string; comentario?: string }>({});
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [publicada, setPublicada] = useState<Resena | null>(null);
  const [yaExistia, setYaExistia] = useState(false);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const texto = comentario.trim();
    // Caracteres como los cuenta el backend: un emoji cuenta como uno
    const largo = [...texto].length;
    const nuevos: typeof errores = {};
    if (calificacion < 1) nuevos.calificacion = 'Elige una calificación de 1 a 5 estrellas.';
    if (largo < LARGO_MINIMO_COMENTARIO || largo > LARGO_MAXIMO_COMENTARIO) {
      nuevos.comentario = `El comentario debe tener entre ${LARGO_MINIMO_COMENTARIO} y ${LARGO_MAXIMO_COMENTARIO} caracteres.`;
    }
    setErrores(nuevos);
    if (nuevos.calificacion || nuevos.comentario) return;

    setEnviando(true);
    setError(null);
    try {
      setPublicada(await publicarResena(reservaId, calificacion, texto));
    } catch (e) {
      if (e instanceof ApiError && e.codigo === 'RESENA_DUPLICADA') setYaExistia(true);
      else if (e instanceof ApiError && e.codigo === 'COMENTARIO_INVALIDO') setErrores({ comentario: e.message });
      else setError(mensajeParaUsuario(e));
    } finally {
      setEnviando(false);
    }
  }

  if (yaExistia) {
    return (
      <div className="alerta alerta--info" role="status">
        <Info size={16} aria-hidden="true" />
        <span>Ya dejaste una reseña para esta estadía. Solo se permite una por reserva.</span>
      </div>
    );
  }

  if (publicada) {
    return (
      <section className="tarjeta tarjeta--relleno">
        <div className="alerta alerta--exito" role="status">
          <CircleCheck size={16} aria-hidden="true" />
          <span>¡Gracias! Tu reseña ya aparece en el alojamiento.</span>
        </div>
        <div className="resena-publicada">
          <Estrellas valor={publicada.calificacion} />
          <p>{publicada.comentario}</p>
        </div>
      </section>
    );
  }

  const mostrada = resaltada || calificacion;

  return (
    <form className="tarjeta tarjeta--relleno" onSubmit={enviar} noValidate>
      <h2 className="tarjeta__titulo">¿Cómo te fue en tu estadía?</h2>
      <p className="campo__ayuda">Tu reseña ayuda a otros huéspedes. Puedes dejarla hasta el {formatearFecha(limite)}.</p>

      {error && (
        <div className="alerta alerta--error" role="alert">
          <CircleAlert size={16} aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}

      <fieldset className="selector-estrellas" disabled={enviando}>
        <legend className="campo__etiqueta">Calificación</legend>
        <div className="selector-estrellas__opciones" onMouseLeave={() => setResaltada(0)}>
          {[1, 2, 3, 4, 5].map((valor) => (
            <label
              key={valor}
              className={valor <= mostrada ? 'selector-estrellas__estrella selector-estrellas__estrella--activa' : 'selector-estrellas__estrella'}
              onMouseEnter={() => setResaltada(valor)}
            >
              <input
                type="radio"
                name="calificacion"
                value={valor}
                checked={calificacion === valor}
                onChange={() => {
                  setCalificacion(valor);
                  setErrores((actual) => ({ ...actual, calificacion: undefined }));
                }}
                className="solo-lectores"
              />
              <Star size={26} aria-hidden="true" />
              <span className="solo-lectores">
                {valor} {valor === 1 ? 'estrella' : 'estrellas'}
              </span>
            </label>
          ))}
          <span className="selector-estrellas__texto">{TEXTO_CALIFICACION[mostrada]}</span>
        </div>
        {errores.calificacion && <p className="campo__error">{errores.calificacion}</p>}
      </fieldset>

      <CampoAreaTexto
        etiqueta="Comentario"
        placeholder="Cuéntanos qué te gustó y qué se puede mejorar"
        rows={4}
        maxLength={LARGO_MAXIMO_COMENTARIO}
        value={comentario}
        onChange={(e) => {
          setComentario(e.target.value);
          setErrores((actual) => ({ ...actual, comentario: undefined }));
        }}
        error={errores.comentario}
        ayuda={`${[...comentario.trim()].length}/${LARGO_MAXIMO_COMENTARIO}`}
        disabled={enviando}
      />

      <button type="submit" className="boton boton--primario" disabled={enviando}>
        {enviando && <LoaderCircle size={16} className="girando" aria-hidden="true" />}
        {enviando ? 'Publicando…' : 'Publicar reseña'}
      </button>
    </form>
  );
}
