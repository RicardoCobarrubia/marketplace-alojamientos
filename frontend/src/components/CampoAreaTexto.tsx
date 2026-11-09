import { useId, type TextareaHTMLAttributes } from 'react';

interface PropiedadesCampoAreaTexto extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  etiqueta: string;
  error?: string;
  ayuda?: string;
}

export function CampoAreaTexto({ etiqueta, error, ayuda, id, ...resto }: PropiedadesCampoAreaTexto) {
  const idGenerado = useId();
  const idCampo = id ?? idGenerado;
  const idMensaje = `${idCampo}-mensaje`;
  const mensaje = error ?? ayuda;

  return (
    <div className="campo">
      <label htmlFor={idCampo} className="campo__etiqueta">
        {etiqueta}
      </label>
      <textarea
        id={idCampo}
        className={error ? 'campo__area campo__area--error' : 'campo__area'}
        aria-invalid={error ? true : undefined}
        aria-describedby={mensaje ? idMensaje : undefined}
        {...resto}
      />
      {mensaje && (
        <p id={idMensaje} className={error ? 'campo__error' : 'campo__ayuda'}>
          {mensaje}
        </p>
      )}
    </div>
  );
}
