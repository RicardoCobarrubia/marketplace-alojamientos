import { useId, type InputHTMLAttributes, type ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';

interface PropiedadesCampoTexto extends InputHTMLAttributes<HTMLInputElement> {
  etiqueta: string;
  icono?: LucideIcon;
  error?: string;
  /** Texto de ayuda debajo del campo; se reemplaza por el error cuando hay uno. */
  ayuda?: string;
  /** Elemento al final del campo, por ejemplo el botón para ver la contraseña. */
  accesorio?: ReactNode;
}

export function CampoTexto({ etiqueta, icono: Icono, error, ayuda, accesorio, id, ...resto }: PropiedadesCampoTexto) {
  const idGenerado = useId();
  const idCampo = id ?? idGenerado;
  const idMensaje = `${idCampo}-mensaje`;
  const mensaje = error ?? ayuda;

  return (
    <div className="campo">
      <label htmlFor={idCampo} className="campo__etiqueta">
        {etiqueta}
      </label>
      <div className={error ? 'campo__control campo__control--error' : 'campo__control'}>
        {Icono && <Icono className="campo__icono" size={16} aria-hidden="true" />}
        <input
          id={idCampo}
          className="campo__input"
          aria-invalid={error ? true : undefined}
          aria-describedby={mensaje ? idMensaje : undefined}
          {...resto}
        />
        {accesorio}
      </div>
      {mensaje && (
        <p id={idMensaje} className={error ? 'campo__error' : 'campo__ayuda'}>
          {mensaje}
        </p>
      )}
    </div>
  );
}
