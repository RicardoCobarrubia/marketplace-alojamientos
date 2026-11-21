import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ChevronLeft, ChevronRight, CircleAlert, LoaderCircle, MapPin, Users } from 'lucide-react';
import { buscarAlojamientos } from '../../api/busqueda';
import { mensajeParaUsuario } from '../../api/mensajes';
import { useAuth } from '../../auth/useAuth';
import { Miniatura } from '../../components/Miniatura';
import { useCatalogos } from '../../hooks/useCatalogos';
import { RUTAS, rutaAlojamientoHuesped } from '../../routes/rutas';
import type { CriteriosBusqueda, ResultadoBusqueda } from '../../types/busqueda';
import { formatearPrecio, formatearRango } from '../../utils/formato';
import { escribirCriterios, leerCriterios } from './criteriosUrl';
import { FormularioBusqueda } from './FormularioBusqueda';
import './Huesped.css';

/** Lo que recibe el detalle del alojamiento para su enlace "volver". */
export interface VolverABusqueda {
  volver: { ruta: string; etiqueta: string };
}

/**
 * Inicio del huésped: buscar alojamientos publicados con filtros (MA-61, RF-16, RF-17). Sin
 * filtros lista todos, del más barato al más caro. Los filtros quedan en la URL.
 */
export default function ExplorarPage() {
  const { usuario } = useAuth();
  const { catalogos } = useCatalogos();
  const [parametros, setParametros] = useSearchParams();
  const consulta = parametros.toString();
  const criterios = useMemo(() => leerCriterios(new URLSearchParams(consulta)), [consulta]);
  const [resultado, setResultado] = useState<ResultadoBusqueda | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    let activo = true;
    setCargando(true);
    setError(null);
    buscarAlojamientos(criterios)
      .then((r) => activo && setResultado(r))
      .catch((e: unknown) => activo && setError(mensajeParaUsuario(e)))
      .finally(() => activo && setCargando(false));
    return () => {
      activo = false;
    };
  }, [criterios]);

  function buscar(nuevos: CriteriosBusqueda) {
    setParametros(escribirCriterios({ ...nuevos, pagina: 0 }));
  }

  function irAPagina(pagina: number) {
    setParametros(escribirCriterios({ ...criterios, pagina }));
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  const estadia = { llegada: criterios.llegada, salida: criterios.salida, huespedes: criterios.huespedes };
  const volver: VolverABusqueda = {
    volver: { ruta: `${RUTAS.huesped}${consulta ? `?${consulta}` : ''}`, etiqueta: 'Volver a los resultados' },
  };
  const rangoFechas =
    criterios.llegada && criterios.salida ? ` disponibles del ${formatearRango(criterios.llegada, criterios.salida)}` : '';

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Hola, {usuario?.nombre.split(' ')[0]}</h1>
          <p>¿A dónde quieres ir?</p>
        </div>
      </div>

      {/* key: si cambia la URL (atrás/adelante en el navegador) el formulario toma esos valores */}
      <FormularioBusqueda key={consulta} inicial={criterios} catalogos={catalogos} alBuscar={buscar} />

      {error && (
        <div className="alerta alerta--error" role="alert">
          <CircleAlert size={16} aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}

      {cargando && !resultado && (
        <div className="cargando">
          <LoaderCircle size={18} className="girando" aria-hidden="true" />
          Buscando alojamientos…
        </div>
      )}

      {resultado && !error && (
        <section aria-busy={cargando} className={cargando ? 'resultados resultados--cargando' : 'resultados'}>
          <p className="resultados__resumen" role="status">
            {resultado.total === 0
              ? 'Sin resultados'
              : `${resultado.total} ${resultado.total === 1 ? 'alojamiento' : 'alojamientos'}${rangoFechas} · del más económico al más costoso`}
          </p>

          {resultado.total === 0 ? (
            <div className="tarjeta estado-vacio">
              <h2>No encontramos alojamientos</h2>
              <p>{resultado.mensaje ?? 'Prueba con otras fechas o quita algún filtro.'}</p>
            </div>
          ) : (
            <ul className="resultados__lista">
              {resultado.alojamientos.map((a) => (
                <li key={a.id}>
                  <Link to={rutaAlojamientoHuesped(a.id, estadia)} state={volver} className="tarjeta-alojamiento">
                    <Miniatura url={a.imagenes[0]} alt="" className="tarjeta-alojamiento__foto" />
                    <div className="tarjeta-alojamiento__cuerpo">
                      <h2 className="tarjeta-alojamiento__titulo">{a.titulo}</h2>
                      <p className="tarjeta-alojamiento__dato">
                        <MapPin size={13} aria-hidden="true" />
                        {a.ciudad.nombre}, {a.ciudad.departamento}
                      </p>
                      <p className="tarjeta-alojamiento__dato">
                        <Users size={13} aria-hidden="true" />
                        {a.tipo.nombre} · hasta {a.capacidad} {a.capacidad === 1 ? 'huésped' : 'huéspedes'}
                      </p>
                      <p className="tarjeta-alojamiento__precio">
                        <strong>{formatearPrecio(a.precioNoche)}</strong> / noche
                      </p>
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          )}

          {resultado.totalPaginas > 1 && (
            <nav className="paginacion" aria-label="Páginas de resultados">
              <button
                type="button"
                className="boton boton--secundario boton--pequeno"
                onClick={() => irAPagina(resultado.pagina - 1)}
                disabled={resultado.pagina === 0 || cargando}
              >
                <ChevronLeft size={14} aria-hidden="true" />
                Anterior
              </button>
              <span>
                Página {resultado.pagina + 1} de {resultado.totalPaginas}
              </span>
              <button
                type="button"
                className="boton boton--secundario boton--pequeno"
                onClick={() => irAPagina(resultado.pagina + 1)}
                disabled={resultado.pagina + 1 >= resultado.totalPaginas || cargando}
              >
                Siguiente
                <ChevronRight size={14} aria-hidden="true" />
              </button>
            </nav>
          )}
        </section>
      )}
    </>
  );
}
