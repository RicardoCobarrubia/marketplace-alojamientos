import { useState } from 'react';
import { Check, CircleDollarSign, Pencil, Plus, X } from 'lucide-react';
import { CampoAreaTexto } from '../../../components/CampoAreaTexto';
import { CampoSeleccion } from '../../../components/CampoSeleccion';
import { CampoTexto } from '../../../components/CampoTexto';
import { NOMBRE_MODALIDAD, type Modalidad } from '../../../types/alojamiento';
import { formatearPrecio, leerNumero } from '../../../utils/formato';
import { MAXIMO_REGLAS, nuevaClave, validarDinero, type AdicionalLocal } from './modelo';
import type { PropiedadesPaso } from './tipos';

export function PasoPrecios(props: PropiedadesPaso) {
  const { formulario: f, errores, catalogos, deshabilitado, cambiar } = props;

  function alternarServicio(id: number) {
    cambiar(
      'servicioIds',
      f.servicioIds.includes(id) ? f.servicioIds.filter((s) => s !== id) : [...f.servicioIds, id],
    );
  }

  return (
    <>
      <h2 className="tarjeta__titulo">Precios, servicios y reglas</h2>

      <div className="formulario__fila">
        <CampoTexto
          etiqueta="Precio por noche"
          icono={CircleDollarSign}
          type="number"
          inputMode="decimal"
          min={0}
          step="any"
          placeholder="320000"
          value={f.precioNoche}
          onChange={(e) => cambiar('precioNoche', e.target.value)}
          error={errores.precioNoche}
          ayuda="En pesos colombianos. Debe ser un valor positivo."
          disabled={deshabilitado}
        />
        <CampoTexto
          etiqueta="Tarifa de limpieza"
          icono={CircleDollarSign}
          type="number"
          inputMode="decimal"
          min={0}
          step="any"
          placeholder="60000"
          value={f.tarifaLimpieza}
          onChange={(e) => cambiar('tarifaLimpieza', e.target.value)}
          error={errores.tarifaLimpieza}
          ayuda="Puede ser 0."
          disabled={deshabilitado}
        />
      </div>

      <fieldset className="campo formulario__grupo" disabled={deshabilitado}>
        <legend className="campo__etiqueta">Servicios del alojamiento</legend>
        <div className="formulario__chips">
          {catalogos.servicios.map((s) => {
            const elegido = f.servicioIds.includes(s.id);
            return (
              <button
                key={s.id}
                type="button"
                className={elegido ? 'chip chip--activo' : 'chip'}
                aria-pressed={elegido}
                onClick={() => alternarServicio(s.id)}
              >
                {s.nombre}
              </button>
            );
          })}
        </div>
        {errores.servicioIds && <p className="campo__error">{errores.servicioIds}</p>}
      </fieldset>

      <CampoAreaTexto
        etiqueta="Reglas del alojamiento"
        placeholder={'Check-in desde las 3:00 p. m.\nCheck-out hasta las 11:00 a. m.\nNo se permiten fiestas ni eventos.'}
        rows={4}
        value={f.reglas}
        onChange={(e) => cambiar('reglas', e.target.value)}
        error={errores.reglas}
        ayuda={`Escribe una regla por línea (máximo ${MAXIMO_REGLAS}).`}
        disabled={deshabilitado}
      />

      <ServiciosAdicionales {...props} />
    </>
  );
}

interface BorradorAdicional {
  nombre: string;
  costo: string;
  modalidad: Modalidad;
}

const BORRADOR_VACIO: BorradorAdicional = { nombre: '', costo: '', modalidad: 'POR_ESTADIA' };

/** Servicios opcionales con precio, por ejemplo desayuno o traslado (MA-55, RF-11). */
function ServiciosAdicionales({ formulario: f, deshabilitado, cambiarAdicionales }: PropiedadesPaso) {
  const [abierto, setAbierto] = useState(false);
  const [editando, setEditando] = useState<string | null>(null);
  const [borrador, setBorrador] = useState<BorradorAdicional>(BORRADOR_VACIO);
  const [errores, setErrores] = useState<{ nombre?: string; costo?: string }>({});

  function abrir(item?: AdicionalLocal) {
    setEditando(item?.clave ?? null);
    setBorrador(item ? { nombre: item.nombre, costo: item.costo, modalidad: item.modalidad } : BORRADOR_VACIO);
    setErrores({});
    setAbierto(true);
  }

  function cerrar() {
    setAbierto(false);
    setEditando(null);
    setErrores({});
  }

  function guardar() {
    const nuevosErrores: { nombre?: string; costo?: string } = {};
    if (!borrador.nombre.trim()) nuevosErrores.nombre = 'El nombre es obligatorio.';
    else if (borrador.nombre.trim().length > 100) nuevosErrores.nombre = 'El nombre admite máximo 100 caracteres.';
    const costo = validarDinero(borrador.costo, 'El costo', false);
    if (costo) nuevosErrores.costo = costo;
    setErrores(nuevosErrores);
    if (nuevosErrores.nombre || nuevosErrores.costo) return;

    const datos = { nombre: borrador.nombre.trim(), costo: borrador.costo.trim(), modalidad: borrador.modalidad };
    cambiarAdicionales(
      editando
        ? f.adicionales.map((a) => (a.clave === editando ? { ...a, ...datos } : a))
        : [...f.adicionales, { clave: nuevaClave(), ...datos }],
    );
    cerrar();
  }

  return (
    <div className="adicionales">
      <div className="adicionales__encabezado">
        <span className="campo__etiqueta">Servicios adicionales (opcionales)</span>
        {!abierto && (
          <button type="button" className="boton boton--secundario boton--pequeno" onClick={() => abrir()} disabled={deshabilitado}>
            <Plus size={14} aria-hidden="true" />
            Agregar servicio
          </button>
        )}
      </div>

      {f.adicionales.length > 0 && (
        <table className="adicionales__tabla">
          <thead>
            <tr>
              <th scope="col">Nombre</th>
              <th scope="col">Precio</th>
              <th scope="col">Unidad de cobro</th>
              <th scope="col">
                <span className="solo-lectores">Acciones</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {f.adicionales.map((a) => (
              <tr key={a.clave}>
                <td>{a.nombre}</td>
                <td>{formatearPrecio(leerNumero(a.costo))}</td>
                <td>{NOMBRE_MODALIDAD[a.modalidad]}</td>
                <td className="adicionales__acciones">
                  <button
                    type="button"
                    className="adicionales__boton"
                    onClick={() => abrir(a)}
                    disabled={deshabilitado}
                    aria-label={`Editar ${a.nombre}`}
                  >
                    <Pencil size={14} />
                  </button>
                  <button
                    type="button"
                    className="adicionales__boton"
                    onClick={() => cambiarAdicionales(f.adicionales.filter((x) => x.clave !== a.clave))}
                    disabled={deshabilitado}
                    aria-label={`Quitar ${a.nombre}`}
                  >
                    <X size={15} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {f.adicionales.length === 0 && !abierto && (
        <p className="campo__ayuda">Por ejemplo: desayuno, traslado desde el aeropuerto o tour guiado.</p>
      )}

      {abierto && (
        <div className="adicionales__formulario">
          <CampoTexto
            etiqueta="Nombre del servicio"
            placeholder="Desayuno cafetero"
            maxLength={100}
            value={borrador.nombre}
            onChange={(e) => setBorrador({ ...borrador, nombre: e.target.value })}
            error={errores.nombre}
            autoFocus
          />
          <div className="formulario__fila">
            <CampoTexto
              etiqueta="Precio"
              icono={CircleDollarSign}
              type="number"
              inputMode="decimal"
              min={0}
              step="any"
              placeholder="25000"
              value={borrador.costo}
              onChange={(e) => setBorrador({ ...borrador, costo: e.target.value })}
              error={errores.costo}
            />
            <CampoSeleccion
              etiqueta="Unidad de cobro"
              opciones={(Object.keys(NOMBRE_MODALIDAD) as Modalidad[]).map((m) => ({
                valor: m,
                etiqueta: NOMBRE_MODALIDAD[m],
              }))}
              value={borrador.modalidad}
              onChange={(e) => setBorrador({ ...borrador, modalidad: e.target.value as Modalidad })}
            />
          </div>
          <div className="adicionales__botones">
            <button type="button" className="boton boton--secundario boton--pequeno" onClick={cerrar}>
              Cancelar
            </button>
            <button type="button" className="boton boton--primario boton--pequeno" onClick={guardar}>
              <Check size={14} aria-hidden="true" />
              {editando ? 'Guardar servicio' : 'Agregar'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
