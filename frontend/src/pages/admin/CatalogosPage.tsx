import { useState, type FormEvent } from 'react';
import { CircleAlert, CircleCheck, LoaderCircle, Plus } from 'lucide-react';
import { crearCiudad, crearServicio, crearTipo } from '../../api/admin';
import { ApiError } from '../../api/ApiError';
import { mensajeParaUsuario } from '../../api/mensajes';
import { CampoTexto } from '../../components/CampoTexto';
import { invalidarCatalogos, useCatalogos, type Catalogos } from '../../hooks/useCatalogos';
import './CatalogosPage.css';

/** Mismo formato que exige el backend para códigos de tipos y servicios. */
const FORMATO_CODIGO = /^[A-Z][A-Z0-9_]{1,39}$/;

/** "Aire acondicionado" → "AIRE_ACONDICIONADO" */
function sugerirCodigo(nombre: string): string {
  return nombre
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, '_')
    .replace(/^[^A-Z]+|_+$/g, '')
    .slice(0, 40);
}

/**
 * Catálogos que usan los formularios y la búsqueda. El administrador los amplía sin cambiar
 * código (MA-53, escenario 3). El backend solo permite agregar, no editar ni borrar.
 */
export default function CatalogosPage() {
  const { catalogos, error } = useCatalogos();
  /** Elementos agregados en esta visita, para mostrarlos sin recargar. */
  const [agregados, setAgregados] = useState<Catalogos>({ ciudades: [], tipos: [], servicios: [], politicas: [] });

  if (error) {
    return (
      <div className="alerta alerta--error" role="alert">
        <CircleAlert size={16} aria-hidden="true" />
        <span>{error}</span>
      </div>
    );
  }

  if (!catalogos) {
    return (
      <div className="cargando">
        <LoaderCircle size={18} className="girando" aria-hidden="true" />
        Cargando catálogos…
      </div>
    );
  }

  const ciudades = [...catalogos.ciudades, ...agregados.ciudades];
  const tipos = [...catalogos.tipos, ...agregados.tipos];
  const servicios = [...catalogos.servicios, ...agregados.servicios];

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Catálogos</h1>
          <p>Ciudades, tipos de alojamiento y servicios que eligen los anfitriones</p>
        </div>
      </div>

      <div className="catalogos">
        <SeccionCatalogo
          titulo="Ciudades"
          elementos={ciudades.map((c) => ({ id: c.id, principal: c.nombre, secundario: c.departamento }))}
          campoSecundario={{ etiqueta: 'Departamento', placeholder: 'Quindío', maximo: 80 }}
          placeholderNombre="Salento"
          guardar={async (nombre, departamento) => {
            const ciudad = await crearCiudad({ nombre, departamento });
            setAgregados((a) => ({ ...a, ciudades: [...a.ciudades, ciudad] }));
            return ciudad.nombre;
          }}
        />
        <SeccionCatalogo
          titulo="Tipos de alojamiento"
          elementos={tipos.map((t) => ({ id: t.id, principal: t.nombre, secundario: t.codigo }))}
          campoSecundario={{ etiqueta: 'Código', placeholder: 'GLAMPING', maximo: 40, esCodigo: true }}
          placeholderNombre="Glamping"
          guardar={async (nombre, codigo) => {
            const tipo = await crearTipo({ nombre, codigo });
            setAgregados((a) => ({ ...a, tipos: [...a.tipos, tipo] }));
            return tipo.nombre;
          }}
        />
        <SeccionCatalogo
          titulo="Servicios"
          elementos={servicios.map((s) => ({ id: s.id, principal: s.nombre, secundario: s.codigo }))}
          campoSecundario={{ etiqueta: 'Código', placeholder: 'JACUZZI', maximo: 40, esCodigo: true }}
          placeholderNombre="Jacuzzi"
          guardar={async (nombre, codigo) => {
            const servicio = await crearServicio({ nombre, codigo });
            setAgregados((a) => ({ ...a, servicios: [...a.servicios, servicio] }));
            return servicio.nombre;
          }}
        />
      </div>
    </>
  );
}

interface PropiedadesSeccion {
  titulo: string;
  elementos: { id: number; principal: string; secundario: string }[];
  placeholderNombre: string;
  campoSecundario: { etiqueta: string; placeholder: string; maximo: number; esCodigo?: boolean };
  /** Crea el elemento y devuelve su nombre para el aviso. */
  guardar: (nombre: string, secundario: string) => Promise<string>;
}

function SeccionCatalogo({ titulo, elementos, placeholderNombre, campoSecundario, guardar }: PropiedadesSeccion) {
  const [nombre, setNombre] = useState('');
  const [secundario, setSecundario] = useState('');
  const [secundarioEditado, setSecundarioEditado] = useState(false);
  const [errores, setErrores] = useState<{ nombre?: string; secundario?: string }>({});
  const [aviso, setAviso] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  function cambiarNombre(valor: string) {
    setNombre(valor);
    // El código se sugiere a partir del nombre hasta que el administrador lo edita a mano.
    if (campoSecundario.esCodigo && !secundarioEditado) setSecundario(sugerirCodigo(valor));
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const n = nombre.trim();
    const s = secundario.trim();
    const nuevos: { nombre?: string; secundario?: string } = {};
    if (!n) nuevos.nombre = 'El nombre es obligatorio.';
    else if (n.length > 80) nuevos.nombre = 'El nombre admite máximo 80 caracteres.';
    if (!s) nuevos.secundario = `El ${campoSecundario.etiqueta.toLowerCase()} es obligatorio.`;
    else if (s.length > campoSecundario.maximo)
      nuevos.secundario = `Admite máximo ${campoSecundario.maximo} caracteres.`;
    else if (campoSecundario.esCodigo && !FORMATO_CODIGO.test(s))
      nuevos.secundario = 'Va en mayúsculas, sin espacios ni tildes, por ejemplo AIRE_ACONDICIONADO.';
    setErrores(nuevos);
    setAviso(null);
    setError(null);
    if (nuevos.nombre || nuevos.secundario) return;

    setGuardando(true);
    try {
      const creado = await guardar(n, s);
      invalidarCatalogos();
      setAviso(`«${creado}» se agregó al catálogo.`);
      setNombre('');
      setSecundario('');
      setSecundarioEditado(false);
    } catch (e) {
      if (e instanceof ApiError && e.codigo === 'DATOS_INVALIDOS') {
        setErrores({
          nombre: e.errores.nombre,
          secundario: e.errores.codigo ?? e.errores.departamento,
        });
      } else {
        setError(mensajeParaUsuario(e));
      }
    } finally {
      setGuardando(false);
    }
  }

  return (
    <section className="tarjeta tarjeta--relleno catalogo">
      <h2 className="tarjeta__titulo">
        {titulo} <span className="catalogo__total">{elementos.length}</span>
      </h2>
      <ul className="catalogo__lista">
        {elementos.map((e) => (
          <li key={e.id}>
            <span>{e.principal}</span>
            <span className="catalogo__secundario">{e.secundario}</span>
          </li>
        ))}
      </ul>

      <form className="catalogo__formulario" onSubmit={enviar} noValidate>
        {aviso && (
          <div className="alerta alerta--exito" role="status">
            <CircleCheck size={16} aria-hidden="true" />
            <span>{aviso}</span>
          </div>
        )}
        {error && (
          <div className="alerta alerta--error" role="alert">
            <CircleAlert size={16} aria-hidden="true" />
            <span>{error}</span>
          </div>
        )}
        <CampoTexto
          etiqueta="Nombre"
          placeholder={placeholderNombre}
          maxLength={80}
          value={nombre}
          onChange={(e) => cambiarNombre(e.target.value)}
          error={errores.nombre}
          disabled={guardando}
        />
        <CampoTexto
          etiqueta={campoSecundario.etiqueta}
          placeholder={campoSecundario.placeholder}
          maxLength={campoSecundario.maximo}
          value={secundario}
          onChange={(e) => {
            setSecundario(campoSecundario.esCodigo ? e.target.value.toUpperCase() : e.target.value);
            setSecundarioEditado(true);
          }}
          error={errores.secundario}
          ayuda={campoSecundario.esCodigo ? 'Se sugiere a partir del nombre.' : undefined}
          disabled={guardando}
        />
        <button type="submit" className="boton boton--secundario boton--bloque" disabled={guardando}>
          {guardando ? <LoaderCircle size={15} className="girando" aria-hidden="true" /> : <Plus size={15} aria-hidden="true" />}
          Agregar
        </button>
      </form>
    </section>
  );
}
