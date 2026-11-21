import type { Reserva } from '../types/reserva';
import { NOMBRE_MODALIDAD } from '../types/alojamiento';
import { formatearPrecio } from '../utils/formato';
import './DesgloseReserva.css';

interface PropiedadesDesglose {
  reserva: Reserva;
  /** Muestra lo que recibe el anfitrión: el total sin la tarifa de servicio de la plataforma. */
  paraAnfitrion?: boolean;
}

/**
 * Desglose del precio de una reserva (RF-20): subtotal, descuento por estadía larga, limpieza,
 * servicios adicionales, tarifa de servicio y total. Son los valores guardados al reservar, así
 * que no cambian aunque después cambien los precios del alojamiento.
 */
export function DesgloseReserva({ reserva: r, paraAnfitrion }: PropiedadesDesglose) {
  return (
    <dl className="desglose">
      <div>
        <dt>
          {formatearPrecio(r.precioNoche)} × {r.noches} {r.noches === 1 ? 'noche' : 'noches'}
        </dt>
        <dd>{formatearPrecio(r.subtotal)}</dd>
      </div>
      {r.totalDescuentos > 0 && (
        <div className="desglose__descuento">
          <dt>Descuento por estadía larga</dt>
          <dd>−{formatearPrecio(r.totalDescuentos)}</dd>
        </div>
      )}
      <div>
        <dt>Limpieza</dt>
        <dd>{formatearPrecio(r.limpieza)}</dd>
      </div>
      {r.servicios.map((s) => (
        <div key={s.servicioId}>
          <dt>
            {s.nombre}
            <span className="desglose__detalle">
              {NOMBRE_MODALIDAD[s.modalidad].toLowerCase()}
              {s.cantidad > 1 && ` · ${s.cantidad} × ${formatearPrecio(s.costoUnitario)}`}
            </span>
          </dt>
          <dd>{formatearPrecio(s.valor)}</dd>
        </div>
      ))}
      <div>
        <dt>Tarifa de servicio</dt>
        <dd>{formatearPrecio(r.tarifaServicio)}</dd>
      </div>
      <div className="desglose__total">
        <dt>Total pagado por el huésped</dt>
        <dd>{formatearPrecio(r.total)}</dd>
      </div>
      {paraAnfitrion && (
        <div className="desglose__anfitrion">
          <dt>
            Para ti
            <span className="desglose__detalle">Total sin la tarifa de servicio de la plataforma</span>
          </dt>
          <dd>{formatearPrecio(r.total - r.tarifaServicio)}</dd>
        </div>
      )}
    </dl>
  );
}
