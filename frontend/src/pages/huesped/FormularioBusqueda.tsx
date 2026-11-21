import { useState, type FormEvent } from 'react';
import { Search, SlidersHorizontal, X } from 'lucide-react';
import { CampoSeleccion } from '../../components/CampoSeleccion';
import { CampoTexto } from '../../components/CampoTexto';
import type { Catalogos } from '../../hooks/useCatalogos';
import type { CriteriosBusqueda } from '../../types/busqueda';
import { hoyIso } from '../../utils/formato';
import { contarFiltrosExtra } from './criteriosUrl';

interface PropiedadesFormularioBusqueda {
  inicial: CriteriosBusqueda;
  catalogos: Catalogos | null;
  alBuscar: (criterios: CriteriosBusqueda) => void;
}

/** Los campos como texto, tal como los escribe el huésped. */
interface Borrador {
  ciudadId: string;
  llegada: string;
  salida: string;
  huespedes: string;
  tipoId: string;
  precioMin: string;
  precioMax: string;
  servicioIds: number[];
}

type Errores = Partial<Record<'fechas' | 'huespedes' | 'precio', string>>;

function aBorrador(c: CriteriosBusqueda): Borrador {
  const texto = (v: number | string | undefined) => (v === undefined ? '' : String(v));
  return {
    ciudadId: texto(c.ciudadId),
    llegada: texto(c.llegada),
    salida: texto(c.salida),
    huespedes: texto(c.huespedes),
    tipoId: texto(c.tipoId),
    precioMin: texto(c.precioMin),
    precioMax: texto(c.precioMax),
    servicioIds: c.servicioIds ?? [],
  };
}

/** Mismas reglas que el backend (MA-61, CotizacionService.validarFechas) para no hacer una búsqueda que va a fallar. */
function validar(b: Borrador): Errores {
  const errores: Errores = {};
  if (Boolean(b.llegada) !== Boolean(b.salida)) errores.fechas = 'Elige la llegada y la salida, o ninguna.';
  else if (b.llegada && b.llegada < hoyIso()) errores.fechas = 'La llegada no puede ser antes de hoy.';
  else if (b.llegada && b.salida <= b.llegada) errores.fechas = 'La salida debe ser después de la llegada.';
  const huespedes = Number(b.huespedes);
  if (b.huespedes && (!Number.isInteger(huespedes) || huespedes < 1)) errores.huespedes = 'Debe ser 1 o más.';
  const minimo = Number(b.precioMin);
  const maximo = Number(b.precioMax);
  if ((b.precioMin && (!Number.isFinite(minimo) || minimo < 0)) || (b.precioMax && (!Number.isFinite(maximo) || maximo < 0))) {
    errores.precio = 'Los precios no pueden ser negativos.';
  } else if (b.precioMin && b.precioMax && minimo > maximo) {
    errores.precio = 'El precio mínimo no puede ser mayor que el máximo.';
  }
  return errores;
}

function aCriterios(b: Borrador): CriteriosBusqueda {
  const numero = (v: string) => (v.trim() === '' ? undefined : Number(v));
  return {
    ciudadId: numero(b.ciudadId),
    llegada: b.llegada || undefined,
    salida: b.salida || undefined,
    huespedes: numero(b.huespedes),
    tipoId: numero(b.tipoId),
    precioMin: numero(b.precioMin),
    precioMax: numero(b.precioMax),
    servicioIds: b.servicioIds.length > 0 ? [...b.servicioIds].sort((x, y) => x - y) : undefined,
  };
}

/** Barra de búsqueda del inicio del huésped con sus filtros (MA-61, RF-16, RF-17). */
export function FormularioBusqueda({ inicial, catalogos, alBuscar }: PropiedadesFormularioBusqueda) {
  const [borrador, setBorrador] = useState<Borrador>(() => aBorrador(inicial));
  const [errores, setErrores] = useState<Errores>({});
  const [masFiltros, setMasFiltros] = useState(() => contarFiltrosExtra(inicial) > 0);
  const hoy = hoyIso();
  const extras = contarFiltrosExtra(aCriterios(borrador));
  const hayFiltros = Object.values(aCriterios(borrador)).some((v) => v !== undefined);

  function cambiar<K extends keyof Borrador>(campo: K, valor: Borrador[K]) {
    setBorrador((actual) => ({ ...actual, [campo]: valor }));
    setErrores({});
  }

  function alternarServicio(id: number) {
    cambiar(
      'servicioIds',
      borrador.servicioIds.includes(id) ? borrador.servicioIds.filter((s) => s !== id) : [...borrador.servicioIds, id],
    );
  }

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    const encontrados = validar(borrador);
    setErrores(encontrados);
    if (Object.keys(encontrados).length === 0) alBuscar(aCriterios(borrador));
  }

  function limpiar() {
    const vacio = aBorrador({});
    setBorrador(vacio);
    setErrores({});
    alBuscar({});
  }

  return (
    <form className="tarjeta tarjeta--relleno buscador" onSubmit={enviar} noValidate>
      <div className="buscador__principal">
        <CampoSeleccion
          etiqueta="Destino"
          textoVacio="Todas las ciudades"
          opciones={(catalogos?.ciudades ?? []).map((c) => ({ valor: c.id, etiqueta: `${c.nombre}, ${c.departamento}` }))}
          value={borrador.ciudadId}
          onChange={(e) => cambiar('ciudadId', e.target.value)}
        />
        <CampoTexto
          etiqueta="Llegada"
          type="date"
          min={hoy}
          value={borrador.llegada}
          onChange={(e) => {
            const llegada = e.target.value;
            setBorrador((actual) => ({ ...actual, llegada, salida: actual.salida && actual.salida <= llegada ? '' : actual.salida }));
            setErrores({});
          }}
        />
        <CampoTexto
          etiqueta="Salida"
          type="date"
          min={borrador.llegada || hoy}
          value={borrador.salida}
          onChange={(e) => cambiar('salida', e.target.value)}
        />
        <CampoTexto
          etiqueta="Huéspedes"
          type="number"
          min={1}
          step={1}
          placeholder="Cualquiera"
          value={borrador.huespedes}
          onChange={(e) => cambiar('huespedes', e.target.value)}
          error={errores.huespedes}
        />
        <button type="submit" className="boton boton--primario buscador__boton">
          <Search size={16} aria-hidden="true" />
          Buscar
        </button>
      </div>
      {errores.fechas && <p className="campo__error">{errores.fechas}</p>}

      <div className="buscador__acciones">
        <button
          type="button"
          className="enlace-accion"
          aria-expanded={masFiltros}
          onClick={() => setMasFiltros((abierto) => !abierto)}
        >
          <SlidersHorizontal size={14} aria-hidden="true" />
          {masFiltros ? 'Menos filtros' : 'Más filtros'}
          {extras > 0 && <span className="chip__contador">{extras}</span>}
        </button>
        {hayFiltros && (
          <button type="button" className="enlace-accion" onClick={limpiar}>
            <X size={14} aria-hidden="true" />
            Limpiar filtros
          </button>
        )}
      </div>

      {masFiltros && (
        <div className="buscador__extra">
          <div className="buscador__fila">
            <CampoSeleccion
              etiqueta="Tipo de alojamiento"
              textoVacio="Todos"
              opciones={(catalogos?.tipos ?? []).map((t) => ({ valor: t.id, etiqueta: t.nombre }))}
              value={borrador.tipoId}
              onChange={(e) => cambiar('tipoId', e.target.value)}
            />
            <CampoTexto
              etiqueta="Precio mínimo por noche"
              type="number"
              min={0}
              step={1000}
              placeholder="$ 0"
              value={borrador.precioMin}
              onChange={(e) => cambiar('precioMin', e.target.value)}
            />
            <CampoTexto
              etiqueta="Precio máximo por noche"
              type="number"
              min={0}
              step={1000}
              placeholder="Sin límite"
              value={borrador.precioMax}
              onChange={(e) => cambiar('precioMax', e.target.value)}
            />
          </div>
          {errores.precio && <p className="campo__error">{errores.precio}</p>}
          {catalogos && catalogos.servicios.length > 0 && (
            <fieldset className="buscador__servicios">
              <legend className="campo__etiqueta">Servicios (debe tenerlos todos)</legend>
              <div className="filtros">
                {catalogos.servicios.map((s) => (
                  <button
                    key={s.id}
                    type="button"
                    className={borrador.servicioIds.includes(s.id) ? 'chip chip--activo' : 'chip'}
                    aria-pressed={borrador.servicioIds.includes(s.id)}
                    onClick={() => alternarServicio(s.id)}
                  >
                    {s.nombre}
                  </button>
                ))}
              </div>
            </fieldset>
          )}
        </div>
      )}
    </form>
  );
}
