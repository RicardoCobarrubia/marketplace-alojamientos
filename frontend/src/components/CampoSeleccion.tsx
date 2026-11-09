import { useId, type SelectHTMLAttributes } from 'react';
import { ChevronDown } from 'lucide-react';

interface Opcion {
  valor: string | number;
  etiqueta: string;
}

interface PropiedadesCampoSeleccion extends SelectHTMLAttributes<HTMLSelectElement> {
  etiqueta: string;
  opciones: Opcion[];
  /** Texto de la opción vacía inicial, por ejemplo "Elige una ciudad". */
  textoVacio?: string;
  error?: string;
}

export function CampoSeleccion({ etiqueta, opciones, textoVacio, error, id, ...resto }: PropiedadesCampoSeleccion) {
  const idGenerado = useId();
  const idCampo = id ?? idGenerado;
  const idMensaje = `${idCampo}-mensaje`;

  return (
    <div className="campo">
      <label htmlFor={idCampo} className="campo__etiqueta">
        {etiqueta}
      </label>
      <div className={error ? 'campo__control campo__control--error' : 'campo__control'}>
        <select
          id={idCampo}
          className="campo__input campo__select"
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? idMensaje : undefined}
          {...resto}
        >
          {textoVacio !== undefined && <option value="">{textoVacio}</option>}
          {opciones.map((opcion) => (
            <option key={opcion.valor} value={opcion.valor}>
              {opcion.etiqueta}
            </option>
          ))}
        </select>
        <ChevronDown className="campo__icono" size={16} aria-hidden="true" />
      </div>
      {error && (
        <p id={idMensaje} className="campo__error">
          {error}
        </p>
      )}
    </div>
  );
}
