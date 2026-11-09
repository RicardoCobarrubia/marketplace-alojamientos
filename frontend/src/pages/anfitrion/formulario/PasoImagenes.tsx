import { useState, type FormEvent } from 'react';
import { ImagePlus, Link2, Star, X } from 'lucide-react';
import { CampoTexto } from '../../../components/CampoTexto';
import { Miniatura } from '../../../components/Miniatura';
import { esUrlImagen, MAXIMO_IMAGENES } from './modelo';
import type { PropiedadesPaso } from './tipos';

export function PasoImagenes({ formulario: f, errores, catalogos, deshabilitado, cambiar }: PropiedadesPaso) {
  const [url, setUrl] = useState('');
  const [errorUrl, setErrorUrl] = useState<string | undefined>();
  const lleno = f.imagenes.length >= MAXIMO_IMAGENES;

  function agregar(evento?: FormEvent) {
    evento?.preventDefault();
    const limpia = url.trim();
    if (!esUrlImagen(limpia)) return setErrorUrl('Pega un enlace válido que empiece por https://');
    if (limpia.length > 500) return setErrorUrl('El enlace admite máximo 500 caracteres.');
    if (f.imagenes.includes(limpia)) return setErrorUrl('Esa imagen ya está agregada.');
    cambiar('imagenes', [...f.imagenes, limpia]);
    setUrl('');
    setErrorUrl(undefined);
  }

  function hacerPrincipal(indice: number) {
    const copia = [...f.imagenes];
    const [elegida] = copia.splice(indice, 1);
    cambiar('imagenes', [elegida, ...copia]);
  }

  return (
    <>
      <h2 className="tarjeta__titulo">Imágenes y política de cancelación</h2>

      <div className={errores.imagenes ? 'imagenes__zona imagenes__zona--error' : 'imagenes__zona'}>
        <ImagePlus size={22} aria-hidden="true" />
        <strong>Agrega tus imágenes con su enlace</strong>
        <span>
          De 1 a {MAXIMO_IMAGENES} imágenes · {f.imagenes.length} de {MAXIMO_IMAGENES} agregadas. La primera es la
          principal.
        </span>
        <div className="imagenes__agregar">
          <CampoTexto
            etiqueta="Enlace de la imagen"
            icono={Link2}
            type="url"
            placeholder="https://…/foto.jpg"
            value={url}
            onChange={(e) => {
              setUrl(e.target.value);
              setErrorUrl(undefined);
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') agregar(e);
            }}
            error={errorUrl}
            disabled={deshabilitado || lleno}
          />
          <button
            type="button"
            className="boton boton--secundario"
            onClick={() => agregar()}
            disabled={deshabilitado || lleno || !url.trim()}
          >
            Agregar
          </button>
        </div>
      </div>
      {errores.imagenes && <p className="campo__error imagenes__error">{errores.imagenes}</p>}

      {f.imagenes.length > 0 && (
        <ul className="imagenes__galeria">
          {f.imagenes.map((imagen, indice) => (
            <li key={imagen} className="imagenes__item">
              <Miniatura url={imagen} alt={`Imagen ${indice + 1}`} className="imagenes__miniatura" />
              {indice === 0 ? (
                <span className="imagenes__principal">
                  <Star size={11} aria-hidden="true" /> Principal
                </span>
              ) : (
                <button
                  type="button"
                  className="imagenes__usar"
                  onClick={() => hacerPrincipal(indice)}
                  disabled={deshabilitado}
                >
                  Usar como principal
                </button>
              )}
              <button
                type="button"
                className="imagenes__quitar"
                onClick={() => cambiar('imagenes', f.imagenes.filter((_, i) => i !== indice))}
                disabled={deshabilitado}
                aria-label={`Quitar imagen ${indice + 1}`}
              >
                <X size={14} />
              </button>
            </li>
          ))}
        </ul>
      )}

      <fieldset className="campo formulario__grupo" disabled={deshabilitado}>
        <legend className="campo__etiqueta">Política de cancelación</legend>
        <div className="politicas">
          {catalogos.politicas.map((p) => {
            const elegida = f.politicaCancelacionId === String(p.id);
            return (
              <label key={p.id} className={elegida ? 'politica politica--activa' : 'politica'}>
                <input
                  type="radio"
                  name="politica"
                  value={p.id}
                  checked={elegida}
                  onChange={() => cambiar('politicaCancelacionId', String(p.id))}
                />
                <span>{p.nombre}</span>
              </label>
            );
          })}
        </div>
        {errores.politicaCancelacionId && <p className="campo__error">{errores.politicaCancelacionId}</p>}
      </fieldset>
    </>
  );
}
