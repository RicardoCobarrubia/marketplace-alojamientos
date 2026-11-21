import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router';
import { CircleAlert, CircleCheck, Download, FileSpreadsheet, FileText, LoaderCircle } from 'lucide-react';
import { exportarReporte, nombreReporte, type FormatoReporte } from '../../api/admin';
import { mensajeParaUsuario } from '../../api/mensajes';
import { CampoTexto } from '../../components/CampoTexto';
import { guardarArchivo } from '../../utils/descarga';
import { aIso, desdeIso, formatearFecha, hoyIso } from '../../utils/formato';
import './ReportesPage.css';

/** Igual que ReporteService.DIAS_MAXIMOS en el backend. */
const DIAS_MAXIMOS = 366;

const RANGOS = [
  { dias: 7, etiqueta: '7 días' },
  { dias: 30, etiqueta: '30 días' },
  { dias: 90, etiqueta: '90 días' },
  { dias: 365, etiqueta: '1 año' },
];

const FORMATOS: { valor: FormatoReporte; titulo: string; descripcion: string; icono: typeof FileText }[] = [
  { valor: 'PDF', titulo: 'PDF', descripcion: 'Para leer, imprimir o adjuntar', icono: FileText },
  { valor: 'EXCEL', titulo: 'Excel', descripcion: 'Para filtrar y hacer cálculos', icono: FileSpreadsheet },
];

function ultimosDias(dias: number): { desde: string; hasta: string } {
  const hoy = new Date();
  return { desde: aIso(new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate() - (dias - 1))), hasta: aIso(hoy) };
}

function diasEntre(desde: string, hasta: string): number {
  return Math.round((desdeIso(hasta).getTime() - desdeIso(desde).getTime()) / 86_400_000) + 1;
}

/**
 * Reportes de gestión (MA-85, MA-86, RF-41, RF-42): usuarios, alojamientos, reservas, ingresos
 * estimados y demanda por ciudad y tipo, en PDF o Excel, para un rango de fechas.
 */
export default function ReportesPage() {
  const [parametros] = useSearchParams();
  const diasIniciales = Number(parametros.get('dias')) || 30;
  const rangoInicial = ultimosDias(diasIniciales);

  const [formato, setFormato] = useState<FormatoReporte>('PDF');
  const [desde, setDesde] = useState(rangoInicial.desde);
  const [hasta, setHasta] = useState(rangoInicial.hasta);
  const [errores, setErrores] = useState<{ desde?: string; hasta?: string }>({});
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [generando, setGenerando] = useState(false);

  const hoy = hoyIso();
  const rangoActivo = RANGOS.find((r) => {
    const rango = ultimosDias(r.dias);
    return rango.desde === desde && rango.hasta === hasta;
  })?.dias;

  function elegirRango(dias: number) {
    const rango = ultimosDias(dias);
    setDesde(rango.desde);
    setHasta(rango.hasta);
    setErrores({});
  }

  async function descargar(evento: FormEvent) {
    evento.preventDefault();
    const nuevos: { desde?: string; hasta?: string } = {};
    if (!desde) nuevos.desde = 'Elige la fecha inicial.';
    if (!hasta) nuevos.hasta = 'Elige la fecha final.';
    else if (desde && hasta < desde) nuevos.hasta = 'La fecha final no puede ser anterior a la inicial.';
    else if (desde && diasEntre(desde, hasta) > DIAS_MAXIMOS) nuevos.hasta = `El reporte puede cubrir máximo ${DIAS_MAXIMOS} días.`;
    setErrores(nuevos);
    setError(null);
    setAviso(null);
    if (nuevos.desde || nuevos.hasta) return;

    setGenerando(true);
    try {
      const archivo = await exportarReporte(formato, desde, hasta);
      const nombre = nombreReporte(formato, desde, hasta);
      guardarArchivo(archivo, nombre);
      setAviso(`Se descargó «${nombre}» (${(archivo.size / 1024).toFixed(0)} KB). La descarga quedó registrada en la auditoría.`);
    } catch (e) {
      setError(mensajeParaUsuario(e));
    } finally {
      setGenerando(false);
    }
  }

  return (
    <>
      <div className="encabezado-pagina">
        <div>
          <h1>Reportes</h1>
          <p>Exporta el reporte de gestión de la plataforma</p>
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

      <div className="reportes">
        <form className="tarjeta tarjeta--relleno" onSubmit={descargar} noValidate>
          <fieldset className="campo reportes__grupo" disabled={generando}>
            <legend className="campo__etiqueta">Formato</legend>
            <div className="reportes__formatos">
              {FORMATOS.map((f) => {
                const Icono = f.icono;
                return (
                  <label key={f.valor} className={formato === f.valor ? 'reportes__formato reportes__formato--activo' : 'reportes__formato'}>
                    <input
                      type="radio"
                      name="formato"
                      value={f.valor}
                      checked={formato === f.valor}
                      onChange={() => setFormato(f.valor)}
                      className="solo-lectores"
                    />
                    <Icono size={22} aria-hidden="true" />
                    <span>
                      <strong>{f.titulo}</strong>
                      <span className="reportes__formato-detalle">{f.descripcion}</span>
                    </span>
                  </label>
                );
              })}
            </div>
          </fieldset>

          <fieldset className="campo reportes__grupo" disabled={generando}>
            <legend className="campo__etiqueta">Periodo</legend>
            <div className="formulario__chips">
              {RANGOS.map((r) => (
                <button
                  key={r.dias}
                  type="button"
                  className={rangoActivo === r.dias ? 'chip chip--activo' : 'chip'}
                  aria-pressed={rangoActivo === r.dias}
                  onClick={() => elegirRango(r.dias)}
                >
                  Últimos {r.etiqueta}
                </button>
              ))}
            </div>
            <div className="formulario__fila reportes__fechas">
              <CampoTexto
                etiqueta="Desde"
                type="date"
                max={hasta || hoy}
                value={desde}
                onChange={(e) => {
                  setDesde(e.target.value);
                  setErrores({});
                }}
                error={errores.desde}
              />
              <CampoTexto
                etiqueta="Hasta"
                type="date"
                min={desde}
                max={hoy}
                value={hasta}
                onChange={(e) => {
                  setHasta(e.target.value);
                  setErrores({});
                }}
                error={errores.hasta}
              />
            </div>
            {desde && hasta && hasta >= desde && (
              <p className="campo__ayuda reportes__resumen">
                {formatearFecha(desde)} – {formatearFecha(hasta)} · {diasEntre(desde, hasta)} días (máximo {DIAS_MAXIMOS})
              </p>
            )}
          </fieldset>

          <button type="submit" className="boton boton--primario" disabled={generando}>
            {generando ? <LoaderCircle size={16} className="girando" aria-hidden="true" /> : <Download size={16} aria-hidden="true" />}
            {generando ? 'Generando reporte…' : `Descargar ${formato === 'PDF' ? 'PDF' : 'Excel'}`}
          </button>
        </form>

        <aside className="tarjeta tarjeta--relleno reportes__contenido">
          <h3 className="formulario__lateral-titulo">Qué incluye</h3>
          <ul className="lista-chequeo">
            <li>Resumen: usuarios, alojamientos activos, reservas e ingresos estimados</li>
            <li>Usuarios registrados en el periodo</li>
            <li>Alojamientos creados en el periodo</li>
            <li>Reservas por estado</li>
            <li>Demanda por ciudad y tipo de alojamiento</li>
          </ul>
          <p className="campo__ayuda">Son los mismos datos del panel de administración para ese periodo.</p>
        </aside>
      </div>
    </>
  );
}
