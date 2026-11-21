import { useState } from 'react';
import { CircleAlert, LoaderCircle } from 'lucide-react';
import { ApiError } from '../api/ApiError';
import { mensajeParaUsuario } from '../api/mensajes';
import { CampoAreaTexto } from './CampoAreaTexto';

const MAXIMO_MOTIVO = 500;

interface PropiedadesFormularioMotivo {
  etiqueta: string;
  placeholder?: string;
  /** Texto bajo el campo, por ejemplo quién verá el motivo. */
  ayuda?: string;
  textoConfirmar: string;
  textoEnviando: string;
  /** Debe lanzar el error del backend si falla; el formulario lo muestra. */
  alConfirmar: (motivo: string) => Promise<void>;
  alCancelar: () => void;
}

/**
 * Pide un motivo obligatorio antes de una acción de moderación (cancelar una reserva, ocultar una
 * reseña, bloquear un alojamiento, restringir un usuario). Mismas reglas que MotivoDTO: de 1 a 500
 * caracteres.
 */
export function FormularioMotivo({
  etiqueta,
  placeholder,
  ayuda,
  textoConfirmar,
  textoEnviando,
  alConfirmar,
  alCancelar,
}: PropiedadesFormularioMotivo) {
  const [motivo, setMotivo] = useState('');
  const [errorCampo, setErrorCampo] = useState<string | undefined>();
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function confirmar() {
    const limpio = motivo.trim();
    if (!limpio) return setErrorCampo('Debes indicar el motivo.');
    if (limpio.length > MAXIMO_MOTIVO) return setErrorCampo(`El motivo no puede superar ${MAXIMO_MOTIVO} caracteres.`);
    setEnviando(true);
    setError(null);
    try {
      await alConfirmar(limpio);
    } catch (e) {
      if (e instanceof ApiError && e.codigo === 'DATOS_INVALIDOS' && e.errores.motivo) setErrorCampo(e.errores.motivo);
      else setError(mensajeParaUsuario(e));
      setEnviando(false);
    }
  }

  return (
    <div className="formulario-motivo">
      {error && (
        <div className="alerta alerta--error" role="alert">
          <CircleAlert size={16} aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}
      <CampoAreaTexto
        etiqueta={etiqueta}
        placeholder={placeholder}
        rows={3}
        maxLength={MAXIMO_MOTIVO}
        value={motivo}
        onChange={(e) => {
          setMotivo(e.target.value);
          setErrorCampo(undefined);
        }}
        error={errorCampo}
        ayuda={`${ayuda ? `${ayuda} ` : ''}${motivo.length}/${MAXIMO_MOTIVO}`}
        disabled={enviando}
        autoFocus
      />
      <div className="formulario-motivo__botones">
        <button type="button" className="boton boton--secundario boton--pequeno" onClick={alCancelar} disabled={enviando}>
          Cancelar
        </button>
        <button type="button" className="boton boton--peligro boton--pequeno" onClick={confirmar} disabled={enviando}>
          {enviando && <LoaderCircle size={14} className="girando" aria-hidden="true" />}
          {enviando ? textoEnviando : textoConfirmar}
        </button>
      </div>
    </div>
  );
}
