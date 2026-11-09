import { useState } from 'react';
import { House } from 'lucide-react';
import './Miniatura.css';

interface PropiedadesMiniatura {
  url?: string;
  alt: string;
  className?: string;
}

/** Imagen de un alojamiento; si no carga, muestra el ícono de casa del mockup. */
export function Miniatura({ url, alt, className = '' }: PropiedadesMiniatura) {
  const [fallo, setFallo] = useState(false);
  const clase = `miniatura ${className}`.trim();

  if (!url || fallo) {
    return (
      <span className={`${clase} miniatura--vacia`} role="img" aria-label={alt}>
        <House size={18} strokeWidth={1.5} aria-hidden="true" />
      </span>
    );
  }
  return <img className={clase} src={url} alt={alt} loading="lazy" onError={() => setFallo(true)} />;
}
