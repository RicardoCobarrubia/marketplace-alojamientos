import { Miniatura } from '../../../components/Miniatura';
import { EtiquetaEstado } from '../../../components/EtiquetaEstado';
import type { EstadoAlojamiento } from '../../../types/alojamiento';
import { formatearPrecio, leerNumero } from '../../../utils/formato';
import type { PropiedadesPaso } from './tipos';

interface PropiedadesResumen extends PropiedadesPaso {
  estado: EstadoAlojamiento;
}

export function PasoResumen({ formulario: f, catalogos, estado }: PropiedadesResumen) {
  const ciudad = catalogos.ciudades.find((c) => String(c.id) === f.ciudadId);
  const tipo = catalogos.tipos.find((t) => String(t.id) === f.tipoId);
  const politica = catalogos.politicas.find((p) => String(p.id) === f.politicaCancelacionId);
  const servicios = catalogos.servicios.filter((s) => f.servicioIds.includes(s.id)).map((s) => s.nombre);

  return (
    <>
      <h2 className="tarjeta__titulo">Resumen del alojamiento</h2>
      <div className="resumen__cabecera">
        <Miniatura url={f.imagenes[0]} alt={f.titulo || 'Imagen principal'} className="resumen__imagen" />
        <div>
          <div className="resumen__titulo">{f.titulo || 'Sin título'}</div>
          <div className="resumen__detalle">
            {[tipo?.nombre, ciudad && `${ciudad.nombre}, ${ciudad.departamento}`, f.capacidad && `hasta ${f.capacidad} huéspedes`]
              .filter(Boolean)
              .join(' · ')}
          </div>
          <EtiquetaEstado estado={estado} />
        </div>
      </div>

      <dl className="resumen__datos">
        <div>
          <dt>Precio por noche</dt>
          <dd>{f.precioNoche ? formatearPrecio(leerNumero(f.precioNoche)) : '—'}</dd>
        </div>
        <div>
          <dt>Tarifa de limpieza</dt>
          <dd>{f.tarifaLimpieza ? formatearPrecio(leerNumero(f.tarifaLimpieza)) : '—'}</dd>
        </div>
        <div>
          <dt>Servicios</dt>
          <dd>{servicios.length ? servicios.join(', ') : '—'}</dd>
        </div>
        <div>
          <dt>Servicios adicionales</dt>
          <dd>{f.adicionales.length}</dd>
        </div>
        <div>
          <dt>Imágenes</dt>
          <dd>{f.imagenes.length}</dd>
        </div>
        <div>
          <dt>Política de cancelación</dt>
          <dd>{politica?.nombre ?? '—'}</dd>
        </div>
      </dl>
    </>
  );
}
