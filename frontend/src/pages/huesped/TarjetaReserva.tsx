import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { LoaderCircle, TriangleAlert } from 'lucide-react';
import { ApiError } from '../../api/ApiError';
import { cotizar } from '../../api/cotizacion';
import { consultarDisponibilidad } from '../../api/disponibilidad';
import { mensajeParaUsuario } from '../../api/mensajes';
import { crearReserva, estaEnProceso } from '../../api/reservas';
import { CampoTexto } from '../../components/CampoTexto';
import { DesgloseReserva } from '../../components/DesgloseReserva';
import { rutaReservaHuesped, type DatosEstadia } from '../../routes/rutas';
import type { Alojamiento, MotivoNoDisponible, PeriodoDisponible } from '../../types/alojamiento';
import { NOMBRE_MODALIDAD } from '../../types/alojamiento';
import type { Cotizacion, DatosReserva } from '../../types/reserva';
import { formatearPrecio, hoyIso } from '../../utils/formato';
import { VentanaPago } from './VentanaPago';

const MOTIVO_NO_DISPONIBLE: Record<MotivoNoDisponible, string> = {
  NO_ACTIVO: 'El alojamiento no está recibiendo reservas en este momento.',
  FUERA_DE_PERIODO: 'El anfitrión no tiene abiertas esas fechas. Revisa las fechas disponibles.',
  OCUPADO: 'Esas fechas ya están reservadas. Prueba con otras.',
};

type Consulta =
  | { tipo: 'vacia' }
  | { tipo: 'cargando' }
  | { tipo: 'no-disponible'; mensaje: string }
  | { tipo: 'error'; mensaje: string }
  | { tipo: 'cotizada'; cotizacion: Cotizacion };

interface PropiedadesTarjetaReserva {
  alojamiento: Alojamiento;
  periodos: PeriodoDisponible[];
  /** Fechas y huéspedes que trae el buscador, para cotizar apenas se abre. */
  inicial?: DatosEstadia;
}

/**
 * Tarjeta lateral para elegir fechas, huéspedes y servicios adicionales, ver la cotización con su
 * desglose (MA-63, MA-64) y reservar pagando (MA-66, MA-67).
 */
export function TarjetaReserva({ alojamiento: a, periodos, inicial = {} }: PropiedadesTarjetaReserva) {
  const navegar = useNavigate();
  const hoy = hoyIso();
  const [llegada, setLlegada] = useState(inicial.llegada ?? '');
  const [salida, setSalida] = useState(inicial.salida ?? '');
  const [huespedes, setHuespedes] = useState(String(inicial.huespedes ?? 1));
  const [servicioIds, setServicioIds] = useState<number[]>([]);
  const [consulta, setConsulta] = useState<Consulta>({ tipo: 'vacia' });
  // Mientras la ventana de pago está abierta no se cambian las fechas ni los servicios
  const [pagoAbierto, setPagoAbierto] = useState(false);
  const [revisarDeNuevo, setRevisarDeNuevo] = useState(0);

  const numeroHuespedes = Number(huespedes);
  const huespedesValidos = Number.isInteger(numeroHuespedes) && numeroHuespedes >= 1 && numeroHuespedes <= a.capacidad;
  const fechasCompletas = Boolean(llegada && salida);
  const errorFechas =
    llegada && llegada < hoy
      ? 'La llegada no puede ser antes de hoy.'
      : fechasCompletas && salida <= llegada
        ? 'La salida debe ser después de la llegada.'
        : undefined;

  const datos: DatosReserva | null =
    fechasCompletas && !errorFechas && huespedesValidos
      ? { alojamientoId: a.id, llegada, salida, huespedes: numeroHuespedes, servicioIds: [...servicioIds].sort((x, y) => x - y) }
      : null;
  const huellaDatos = datos ? JSON.stringify(datos) : '';

  // Una Idempotency-Key por intento: si se reintenta lo mismo (doble clic, sin respuesta de la
  // pasarela) se reusa y el backend no cobra dos veces; si cambia algo, se genera otra.
  const clave = useRef({ huella: '', valor: '' });
  function claveDelIntento(): string {
    if (clave.current.huella !== huellaDatos) clave.current = { huella: huellaDatos, valor: crypto.randomUUID() };
    return clave.current.valor;
  }

  const serviciosConsulta = useMemo(() => servicioIds.join(','), [servicioIds]);

  useEffect(() => {
    if (!fechasCompletas || errorFechas) {
      setConsulta({ tipo: 'vacia' });
      return;
    }
    let activo = true;
    setConsulta({ tipo: 'cargando' });
    const espera = setTimeout(() => {
      const servicios = serviciosConsulta ? serviciosConsulta.split(',').map(Number) : [];
      Promise.all([consultarDisponibilidad(a.id, llegada, salida), cotizar(a.id, llegada, salida, servicios)])
        .then(([disponibilidad, cotizacion]) => {
          if (!activo) return;
          if (!disponibilidad.disponible) {
            const mensaje = disponibilidad.motivo ? MOTIVO_NO_DISPONIBLE[disponibilidad.motivo] : 'Esas fechas no están disponibles.';
            setConsulta({ tipo: 'no-disponible', mensaje });
          } else {
            setConsulta({ tipo: 'cotizada', cotizacion });
          }
        })
        .catch((e: unknown) => activo && setConsulta({ tipo: 'error', mensaje: mensajeParaUsuario(e) }));
    }, 300);
    return () => {
      activo = false;
      clearTimeout(espera);
    };
  }, [a.id, llegada, salida, serviciosConsulta, fechasCompletas, errorFechas, revisarDeNuevo]);

  function alternarServicio(id: number) {
    setServicioIds((actuales) => (actuales.includes(id) ? actuales.filter((s) => s !== id) : [...actuales, id]));
  }

  /** Lo llama la ventana de pago después de validar la tarjeta. Si falla, lanza el error para que la ventana lo muestre. */
  async function pagar(medioPago: string) {
    if (!datos || consulta.tipo !== 'cotizada') return;
    try {
      const respuesta = await crearReserva(datos, claveDelIntento());
      const reservaId = estaEnProceso(respuesta) ? respuesta.reservaId : respuesta.id;
      navegar(rutaReservaHuesped(reservaId), { state: { recienReservada: true, medioPago } });
    } catch (e) {
      if (e instanceof ApiError && e.codigo === 'FECHAS_NO_DISPONIBLES') {
        // Otra persona tomó las fechas: se cierra el pago y se vuelve a consultar para mostrar el motivo
        setPagoAbierto(false);
        setRevisarDeNuevo((n) => n + 1);
      }
      throw e;
    }
  }

  // Solo se ofrecen como mínimo las fechas que abrió el anfitrión
  const primerDia = periodos.length > 0 && periodos[0].fechaInicio > hoy ? periodos[0].fechaInicio : hoy;

  return (
    <aside className="tarjeta tarjeta--relleno tarjeta-reserva">
      <p className="tarjeta-reserva__precio">
        <strong>{formatearPrecio(a.precioNoche)}</strong> / noche
      </p>

      <div className="tarjeta-reserva__fechas">
        <CampoTexto
          etiqueta="Llegada"
          type="date"
          min={primerDia}
          value={llegada}
          onChange={(e) => {
            setLlegada(e.target.value);
            if (salida && e.target.value >= salida) setSalida('');
          }}
          disabled={pagoAbierto}
        />
        <CampoTexto
          etiqueta="Salida"
          type="date"
          min={llegada || primerDia}
          value={salida}
          onChange={(e) => setSalida(e.target.value)}
          disabled={pagoAbierto}
        />
      </div>
      {errorFechas && <p className="campo__error tarjeta-reserva__error-fechas">{errorFechas}</p>}

      <CampoTexto
        etiqueta="Huéspedes"
        type="number"
        min={1}
        max={a.capacidad}
        step={1}
        value={huespedes}
        onChange={(e) => setHuespedes(e.target.value)}
        error={huespedesValidos ? undefined : `Debe ser un número de 1 a ${a.capacidad}.`}
        ayuda={`Máximo ${a.capacidad}`}
        disabled={pagoAbierto}
      />

      {a.serviciosAdicionales.length > 0 && (
        <fieldset className="tarjeta-reserva__servicios" disabled={pagoAbierto}>
          <legend className="campo__etiqueta">Servicios adicionales</legend>
          {a.serviciosAdicionales.map((s) => (
            <label key={s.id} className="opcion-servicio">
              <input type="checkbox" checked={servicioIds.includes(s.id)} onChange={() => alternarServicio(s.id)} />
              <span className="opcion-servicio__nombre">{s.nombre}</span>
              <span className="opcion-servicio__costo">
                {formatearPrecio(s.costo)}
                <small>{NOMBRE_MODALIDAD[s.modalidad].toLowerCase()}</small>
              </span>
            </label>
          ))}
        </fieldset>
      )}

      {consulta.tipo === 'vacia' && !errorFechas && (
        <p className="campo__ayuda">Elige las fechas para ver el precio total.</p>
      )}
      {consulta.tipo === 'cargando' && (
        <div className="cargando cargando--compacto">
          <LoaderCircle size={16} className="girando" aria-hidden="true" />
          Calculando…
        </div>
      )}
      {(consulta.tipo === 'no-disponible' || consulta.tipo === 'error') && (
        <div className="alerta alerta--aviso" role="status">
          <TriangleAlert size={16} aria-hidden="true" />
          <span>{consulta.mensaje}</span>
        </div>
      )}
      {consulta.tipo === 'cotizada' && <DesgloseReserva reserva={consulta.cotizacion} etiquetaTotal="Total" />}

      <button
        type="button"
        className="boton boton--primario boton--bloque"
        onClick={() => setPagoAbierto(true)}
        disabled={consulta.tipo !== 'cotizada' || !datos}
      >
        Reservar y pagar
      </button>
      <p className="campo__ayuda tarjeta-reserva__nota">
        En el siguiente paso pagas con tarjeta. La reserva queda confirmada en cuanto se aprueba el pago.
      </p>

      {consulta.tipo === 'cotizada' && datos && (
        <VentanaPago
          abierta={pagoAbierto}
          resumen={{
            titulo: a.titulo,
            llegada: datos.llegada,
            salida: datos.salida,
            noches: consulta.cotizacion.noches,
            huespedes: datos.huespedes,
            total: consulta.cotizacion.total,
          }}
          alPagar={pagar}
          alCerrar={() => setPagoAbierto(false)}
        />
      )}
    </aside>
  );
}
