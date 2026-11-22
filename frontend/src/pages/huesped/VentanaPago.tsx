import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { CircleAlert, CreditCard, LoaderCircle, Lock, ShieldCheck, X } from 'lucide-react';
import { ApiError } from '../../api/ApiError';
import { mensajeParaUsuario } from '../../api/mensajes';
import { CampoTexto } from '../../components/CampoTexto';
import { rutaReservaHuesped } from '../../routes/rutas';
import { formatearPrecio, formatearRango } from '../../utils/formato';
import {
  formatearNumero,
  formatearVencimiento,
  largoCvv,
  marcaDe,
  NOMBRE_MARCA,
  soloDigitos,
  validarTarjeta,
  type DatosTarjeta,
  type ErroresTarjeta,
} from '../../utils/tarjeta';

const TARJETA_VACIA: DatosTarjeta = { titular: '', numero: '', vencimiento: '', cvv: '' };

/** Pasos que se muestran mientras se procesa, como en una pasarela real. */
const ETAPAS = ['Validando la tarjeta…', 'Conectando con la pasarela de pagos…', 'Confirmando la reserva…'];

export interface ResumenPago {
  titulo: string;
  llegada: string;
  salida: string;
  noches: number;
  huespedes: number;
  total: number;
}

interface PropiedadesVentanaPago {
  abierta: boolean;
  resumen: ResumenPago;
  /**
   * Crea la reserva; si falla lanza el ApiError del backend y la ventana lo muestra. Recibe solo
   * la descripción de la tarjeta ("Visa terminada en 4242"), nunca sus datos.
   */
  alPagar: (medioPago: string) => Promise<void>;
  alCerrar: () => void;
}

/**
 * Pago simulado de la reserva (MA-67, RF-24): el huésped escribe los datos de una tarjeta, se
 * validan en el navegador y luego el backend cobra con su pasarela simulada. Los datos de la
 * tarjeta no se guardan ni se envían: solo existen en esta ventana mientras está abierta.
 */
export function VentanaPago({ abierta, resumen, alPagar, alCerrar }: PropiedadesVentanaPago) {
  const dialogo = useRef<HTMLDialogElement>(null);
  const [tarjeta, setTarjeta] = useState<DatosTarjeta>(TARJETA_VACIA);
  const [errores, setErrores] = useState<ErroresTarjeta>({});
  const [procesando, setProcesando] = useState(false);
  const [etapa, setEtapa] = useState(0);
  const [error, setError] = useState<{ mensaje: string; reservaId: number | null } | null>(null);

  useEffect(() => {
    const d = dialogo.current;
    if (!d) return;
    if (abierta && !d.open) d.showModal();
    if (!abierta && d.open) d.close();
  }, [abierta]);

  // Avanza los mensajes de la pasarela mientras llega la respuesta
  useEffect(() => {
    if (!procesando) return;
    const temporizador = setInterval(() => setEtapa((e) => Math.min(e + 1, ETAPAS.length - 1)), 900);
    return () => clearInterval(temporizador);
  }, [procesando]);

  function cerrar() {
    if (procesando) return;
    // Al cerrar se borran los datos de la tarjeta
    setTarjeta(TARJETA_VACIA);
    setErrores({});
    setError(null);
    alCerrar();
  }

  function cambiar<K extends keyof DatosTarjeta>(campo: K, valor: string) {
    setTarjeta((actual) => ({ ...actual, [campo]: valor }));
    setErrores((actuales) => ({ ...actuales, [campo]: undefined }));
  }

  async function pagar(evento: FormEvent) {
    evento.preventDefault();
    if (procesando) return;
    const encontrados = validarTarjeta(tarjeta);
    setErrores(encontrados);
    if (Object.values(encontrados).some(Boolean)) return;

    setProcesando(true);
    setEtapa(0);
    setError(null);
    try {
      // Una pausa corta para que se vea el paso de validación, como en una pasarela real
      await new Promise((resolver) => setTimeout(resolver, 900));
      await alPagar(`${NOMBRE_MARCA[marcaDe(tarjeta.numero)]} terminada en ${soloDigitos(tarjeta.numero).slice(-4)}`);
    } catch (e) {
      setError({ mensaje: mensajeParaUsuario(e), reservaId: e instanceof ApiError ? e.reservaId : null });
      setProcesando(false);
    }
  }

  const marca = marcaDe(tarjeta.numero);
  const ultimos = soloDigitos(tarjeta.numero).slice(-4);

  return (
    <dialog
      ref={dialogo}
      className="ventana-pago"
      aria-labelledby="titulo-pago"
      onCancel={(e) => {
        e.preventDefault();
        cerrar();
      }}
    >
      <div className="ventana-pago__encabezado">
        <h2 id="titulo-pago">
          <Lock size={18} aria-hidden="true" />
          Pago seguro
        </h2>
        <button type="button" className="boton-icono" onClick={cerrar} disabled={procesando} aria-label="Cerrar">
          <X size={18} aria-hidden="true" />
        </button>
      </div>

      <div className="ventana-pago__resumen">
        <div>
          <strong>{resumen.titulo}</strong>
          <span>
            {formatearRango(resumen.llegada, resumen.salida)} · {resumen.noches} {resumen.noches === 1 ? 'noche' : 'noches'}{' '}
            · {resumen.huespedes} {resumen.huespedes === 1 ? 'huésped' : 'huéspedes'}
          </span>
        </div>
        <span className="ventana-pago__total">{formatearPrecio(resumen.total)}</span>
      </div>

      {procesando ? (
        <div className="ventana-pago__procesando" role="status" aria-live="polite">
          <LoaderCircle size={32} className="girando" aria-hidden="true" />
          <strong>{ETAPAS[etapa]}</strong>
          <span>
            {NOMBRE_MARCA[marca]} terminada en {ultimos}. No cierres esta ventana.
          </span>
        </div>
      ) : (
        <form onSubmit={pagar} noValidate>
          <div className="alerta alerta--info ventana-pago__aviso">
            <ShieldCheck size={16} aria-hidden="true" />
            <span>
              Pago simulado: no se hace ningún cobro real y los datos de la tarjeta no se guardan ni se envían al
              servidor. Puedes usar la tarjeta de prueba 4242 4242 4242 4242.
            </span>
          </div>

          {error && (
            <div className="alerta alerta--error" role="alert">
              <CircleAlert size={16} aria-hidden="true" />
              <span>
                {error.mensaje}
                {error.reservaId && (
                  <>
                    {' '}
                    <Link to={rutaReservaHuesped(error.reservaId)} className="enlace">
                      Ver la reserva #{error.reservaId}
                    </Link>
                  </>
                )}
              </span>
            </div>
          )}

          <CampoTexto
            etiqueta="Nombre en la tarjeta"
            name="titular"
            autoComplete="cc-name"
            placeholder="Como aparece en la tarjeta"
            value={tarjeta.titular}
            onChange={(e) => cambiar('titular', e.target.value)}
            error={errores.titular}
            autoFocus
          />
          <CampoTexto
            etiqueta="Número de la tarjeta"
            icono={CreditCard}
            name="numero"
            inputMode="numeric"
            autoComplete="cc-number"
            placeholder="1234 5678 9012 3456"
            value={tarjeta.numero}
            onChange={(e) => cambiar('numero', formatearNumero(e.target.value))}
            error={errores.numero}
            ayuda={soloDigitos(tarjeta.numero).length >= 2 ? NOMBRE_MARCA[marca] : undefined}
          />
          <div className="ventana-pago__fila">
            <CampoTexto
              etiqueta="Vencimiento"
              name="vencimiento"
              inputMode="numeric"
              autoComplete="cc-exp"
              placeholder="MM/AA"
              value={tarjeta.vencimiento}
              onChange={(e) => cambiar('vencimiento', formatearVencimiento(e.target.value))}
              error={errores.vencimiento}
            />
            <CampoTexto
              etiqueta="Código de seguridad"
              name="cvv"
              type="password"
              inputMode="numeric"
              autoComplete="cc-csc"
              placeholder={largoCvv(marca) === 4 ? '4 dígitos' : '3 dígitos'}
              maxLength={4}
              value={tarjeta.cvv}
              onChange={(e) => cambiar('cvv', soloDigitos(e.target.value))}
              error={errores.cvv}
            />
          </div>

          <button type="submit" className="boton boton--primario boton--bloque">
            <Lock size={15} aria-hidden="true" />
            Pagar {formatearPrecio(resumen.total)}
          </button>
        </form>
      )}
    </dialog>
  );
}
