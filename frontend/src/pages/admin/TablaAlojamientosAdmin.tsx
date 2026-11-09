import { Link } from 'react-router';
import { EtiquetaEstado } from '../../components/EtiquetaEstado';
import { Miniatura } from '../../components/Miniatura';
import { rutaDetallePublicacion } from '../../routes/rutas';
import type { Alojamiento } from '../../types/alojamiento';
import { formatearHace, formatearPrecio } from '../../utils/formato';
import '../anfitrion/MisAlojamientosPage.css';

interface PropiedadesTabla {
  alojamientos: Alojamiento[];
  /** "revision": columnas de la revisión de publicaciones; "catalogo": todos los alojamientos. */
  modo: 'revision' | 'catalogo';
  textoVacio: string;
}

export function TablaAlojamientosAdmin({ alojamientos, modo, textoVacio }: PropiedadesTabla) {
  return (
    <table className="tabla-alojamientos">
      <thead>
        <tr>
          <th scope="col">Alojamiento</th>
          <th scope="col">Anfitrión</th>
          <th scope="col">Ciudad</th>
          {modo === 'catalogo' && <th scope="col">Precio / noche</th>}
          <th scope="col">{modo === 'revision' ? 'Enviado' : 'Estado'}</th>
          <th scope="col">
            <span className="solo-lectores">Acciones</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {alojamientos.map((a) => (
          <tr key={a.id}>
            <td data-titulo="Alojamiento">
              <div className="tabla-alojamientos__alojamiento">
                <Miniatura url={a.imagenes[0]} alt={a.titulo} />
                <div>
                  <div className="tabla-alojamientos__titulo">{a.titulo}</div>
                  <div className="tabla-alojamientos__ciudad">
                    {a.tipo.nombre} · hasta {a.capacidad} huéspedes
                  </div>
                </div>
              </div>
            </td>
            <td data-titulo="Anfitrión">{a.anfitrionNombre}</td>
            <td data-titulo="Ciudad">{a.ciudad.nombre}</td>
            {modo === 'catalogo' && <td data-titulo="Precio / noche">{formatearPrecio(a.precioNoche)}</td>}
            <td data-titulo={modo === 'revision' ? 'Enviado' : 'Estado'}>
              {modo === 'revision' && a.estado === 'PENDIENTE_REVISION' ? (
                <span className="tabla-alojamientos__ciudad">{formatearHace(a.actualizadoEn)}</span>
              ) : (
                <EtiquetaEstado estado={a.estado} />
              )}
            </td>
            <td data-titulo="Acciones">
              <Link
                to={rutaDetallePublicacion(a.id)}
                className={
                  a.estado === 'PENDIENTE_REVISION' ? 'boton boton--primario boton--pequeno' : 'boton boton--secundario boton--pequeno'
                }
              >
                {a.estado === 'PENDIENTE_REVISION' ? 'Revisar' : 'Ver'}
              </Link>
            </td>
          </tr>
        ))}
        {alojamientos.length === 0 && (
          <tr>
            <td colSpan={modo === 'catalogo' ? 6 : 5} className="tabla-alojamientos__sin-resultados">
              {textoVacio}
            </td>
          </tr>
        )}
      </tbody>
    </table>
  );
}
