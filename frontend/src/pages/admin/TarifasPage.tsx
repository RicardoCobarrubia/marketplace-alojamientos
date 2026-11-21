import { useEffect, useState, type FormEvent } from 'react';
import { CircleAlert, CircleCheck, LoaderCircle, Percent } from 'lucide-react';
import { actualizarConfiguracionCotizacion, obtenerConfiguracionCotizacion } from '../../api/admin';
import { ApiError } from '../../api/ApiError';
import { mensajeParaUsuario } from '../../api/mensajes';
import { CampoTexto } from '../../components/CampoTexto';
import type { ConfiguracionCotizacion } from '../../types/admin';
import { formatearHace, leerNumero, tieneMaximoDosDecimales } from '../../utils/formato';
import './TarifasPage.css';

type Campo = 'porcentajeTarifaServicio' | 'porcentajeDescuentoEstadiaLarga' | 'minimoNochesDescuento';
type Errores = Partial<Record<Campo, string>>;

/** Mismas reglas que ConfiguracionCotizacionDTO en el backend. */
function validar(tarifa: string, descuento: string, noches: string): Errores {
  const errores: Errores = {};
  const porcentaje = (texto: string, nombre: string): string | undefined => {
    const valor = leerNumero(texto);
    if (Number.isNaN(valor)) return `${nombre} es obligatorio.`;
    if (valor < 0) return `${nombre} no puede ser negativo.`;
    if (valor > 100) return `${nombre} no puede superar el 100 %.`;
    if (!tieneMaximoDosDecimales(valor)) return `${nombre} admite máximo 2 decimales.`;
    return undefined;
  };
  errores.porcentajeTarifaServicio = porcentaje(tarifa, 'La tarifa de servicio');
  errores.porcentajeDescuentoEstadiaLarga = porcentaje(descuento, 'El descuento');
  const minimo = leerNumero(noches);
  if (!Number.isInteger(minimo)) errores.minimoNochesDescuento = 'El mínimo de noches debe ser un número entero.';
  else if (minimo < 1) errores.minimoNochesDescuento = 'El mínimo de noches debe ser al menos 1.';
  else if (minimo > 365) errores.minimoNochesDescuento = 'El mínimo de noches no puede superar 365.';
  return errores;
}

/**
 * Configuración de la cotización (MA-65, RF-22): porcentaje de la tarifa de servicio y descuento
 * por estadía larga. Los cambios no afectan las reservas ya confirmadas.
 */
export default function TarifasPage() {
  const [actual, setActual] = useState<ConfiguracionCotizacion | null>(null);
  const [tarifa, setTarifa] = useState('');
  const [descuento, setDescuento] = useState('');
  const [noches, setNoches] = useState('');
  const [errores, setErrores] = useState<Errores>({});
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  function cargarEnFormulario(configuracion: ConfiguracionCotizacion) {
    setActual(configuracion);
    setTarifa(String(configuracion.porcentajeTarifaServicio));
    setDescuento(String(configuracion.porcentajeDescuentoEstadiaLarga));
    setNoches(String(configuracion.minimoNochesDescuento));
  }

  useEffect(() => {
    let activo = true;
    obtenerConfiguracionCotizacion()
      .then((configuracion) => activo && cargarEnFormulario(configuracion))
      .catch((e: unknown) => activo && setError(mensajeParaUsuario(e)));
    return () => {
      activo = false;
    };
  }, []);

  async function guardar(evento: FormEvent) {
    evento.preventDefault();
    const nuevos = validar(tarifa, descuento, noches);
    setErrores(nuevos);
    setAviso(null);
    setError(null);
    if (Object.values(nuevos).some(Boolean)) return;

    setGuardando(true);
    try {
      cargarEnFormulario(
        await actualizarConfiguracionCotizacion({
          porcentajeTarifaServicio: leerNumero(tarifa),
          porcentajeDescuentoEstadiaLarga: leerNumero(descuento),
          minimoNochesDescuento: leerNumero(noches),
        }),
      );
      setAviso('Configuración guardada. Se aplica a las cotizaciones nuevas; las reservas confirmadas no cambian.');
    } catch (e) {
      if (e instanceof ApiError && e.codigo === 'DATOS_INVALIDOS') setErrores(e.errores as Errores);
      else setError(mensajeParaUsuario(e));
    } finally {
      setGuardando(false);
    }
  }

  const ejemploTarifa = leerNumero(tarifa);
  const ejemploDescuento = leerNumero(descuento);

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Tarifas</h1>
          <p>Cómo se calcula la cotización de cada reserva</p>
        </div>
      </div>

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

      {!actual && !error && (
        <div className="cargando">
          <LoaderCircle size={18} className="girando" aria-hidden="true" />
          Cargando configuración…
        </div>
      )}

      {actual && (
        <div className="tarifas">
          <form className="tarjeta tarjeta--relleno" onSubmit={guardar} noValidate>
            <h2 className="tarjeta__titulo">Configuración de la cotización</h2>
            <CampoTexto
              etiqueta="Tarifa de servicio (%)"
              icono={Percent}
              type="number"
              inputMode="decimal"
              min={0}
              max={100}
              step="any"
              value={tarifa}
              onChange={(e) => setTarifa(e.target.value)}
              error={errores.porcentajeTarifaServicio}
              ayuda="Se cobra sobre el subtotal con descuento, la limpieza y los servicios adicionales."
              disabled={guardando}
            />
            <div className="formulario__fila">
              <CampoTexto
                etiqueta="Descuento por estadía larga (%)"
                icono={Percent}
                type="number"
                inputMode="decimal"
                min={0}
                max={100}
                step="any"
                value={descuento}
                onChange={(e) => setDescuento(e.target.value)}
                error={errores.porcentajeDescuentoEstadiaLarga}
                disabled={guardando}
              />
              <CampoTexto
                etiqueta="Desde cuántas noches"
                type="number"
                inputMode="numeric"
                min={1}
                max={365}
                step={1}
                value={noches}
                onChange={(e) => setNoches(e.target.value)}
                error={errores.minimoNochesDescuento}
                disabled={guardando}
              />
            </div>
            <div className="tarifas__pie">
              {actual.actualizadoEn && <span className="campo__ayuda">Última actualización {formatearHace(actual.actualizadoEn)}</span>}
              <button type="submit" className="boton boton--primario" disabled={guardando}>
                {guardando && <LoaderCircle size={16} className="girando" aria-hidden="true" />}
                {guardando ? 'Guardando…' : 'Guardar cambios'}
              </button>
            </div>
          </form>

          <aside className="tarjeta tarjeta--relleno tarifas__explicacion">
            <h3 className="formulario__lateral-titulo">Cómo queda una cotización</h3>
            <ol>
              <li>Subtotal = noches × precio por noche.</li>
              <li>
                Si la estadía es de {noches || '…'} noches o más, se descuenta el{' '}
                {Number.isNaN(ejemploDescuento) ? '…' : ejemploDescuento} % del subtotal.
              </li>
              <li>Se suman la limpieza y los servicios adicionales.</li>
              <li>
                Sobre ese valor se cobra la tarifa de servicio del {Number.isNaN(ejemploTarifa) ? '…' : ejemploTarifa} %: son los
                ingresos estimados de la plataforma.
              </li>
            </ol>
          </aside>
        </div>
      )}
    </>
  );
}
