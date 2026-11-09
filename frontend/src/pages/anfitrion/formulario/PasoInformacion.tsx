import { MapPin } from 'lucide-react';
import { CampoAreaTexto } from '../../../components/CampoAreaTexto';
import { CampoSeleccion } from '../../../components/CampoSeleccion';
import { CampoTexto } from '../../../components/CampoTexto';
import type { PropiedadesPaso } from './tipos';

export function PasoInformacion({ formulario: f, errores, catalogos, deshabilitado, cambiar }: PropiedadesPaso) {
  return (
    <>
      <h2 className="tarjeta__titulo">Información básica</h2>

      <CampoTexto
        etiqueta="Título"
        placeholder="Casa cafetera con vista a las montañas"
        maxLength={120}
        value={f.titulo}
        onChange={(e) => cambiar('titulo', e.target.value)}
        error={errores.titulo}
        disabled={deshabilitado}
      />

      <CampoAreaTexto
        etiqueta="Descripción"
        placeholder="Describe el espacio, el entorno y lo que lo hace especial."
        rows={4}
        maxLength={2000}
        value={f.descripcion}
        onChange={(e) => cambiar('descripcion', e.target.value)}
        error={errores.descripcion}
        ayuda="Describe el espacio, el entorno y lo que lo hace especial."
        disabled={deshabilitado}
      />

      <div className="formulario__fila">
        <CampoSeleccion
          etiqueta="Ciudad"
          textoVacio="Elige una ciudad"
          opciones={catalogos.ciudades.map((c) => ({ valor: c.id, etiqueta: `${c.nombre}, ${c.departamento}` }))}
          value={f.ciudadId}
          onChange={(e) => cambiar('ciudadId', e.target.value)}
          error={errores.ciudadId}
          disabled={deshabilitado}
        />
        <CampoSeleccion
          etiqueta="Tipo de alojamiento"
          textoVacio="Elige un tipo"
          opciones={catalogos.tipos.map((t) => ({ valor: t.id, etiqueta: t.nombre }))}
          value={f.tipoId}
          onChange={(e) => cambiar('tipoId', e.target.value)}
          error={errores.tipoId}
          disabled={deshabilitado}
        />
      </div>

      <CampoTexto
        etiqueta="Ubicación"
        icono={MapPin}
        placeholder="Vereda Boquía, km 3 vía al Valle de Cocora"
        maxLength={200}
        value={f.ubicacion}
        onChange={(e) => cambiar('ubicacion', e.target.value)}
        error={errores.ubicacion}
        disabled={deshabilitado}
      />

      <div className="formulario__campo-corto">
        <CampoTexto
          etiqueta="Capacidad (huéspedes)"
          type="number"
          inputMode="numeric"
          min={1}
          step={1}
          placeholder="6"
          value={f.capacidad}
          onChange={(e) => cambiar('capacidad', e.target.value)}
          error={errores.capacidad}
          ayuda="Debe ser mayor que cero."
          disabled={deshabilitado}
        />
      </div>
    </>
  );
}
