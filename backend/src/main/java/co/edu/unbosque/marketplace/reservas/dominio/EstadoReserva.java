package co.edu.unbosque.marketplace.reservas.dominio;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados de la reserva y sus transiciones permitidas (patrón State, ADR-004; MA-71):
 * <ul>
 * <li>PENDIENTE_PAGO → CONFIRMADA o PAGO_FALLIDO</li>
 * <li>CONFIRMADA ↔ EN_REVISION</li>
 * <li>CONFIRMADA o EN_REVISION → CANCELADA</li>
 * <li>CONFIRMADA → COMPLETADA, al pasar la fecha de salida</li>
 * </ul>
 * PAGO_FALLIDO, CANCELADA y COMPLETADA son finales.
 */
public enum EstadoReserva {

	PENDIENTE_PAGO {
		@Override
		public Set<EstadoReserva> siguientes() {
			return EnumSet.of(CONFIRMADA, PAGO_FALLIDO);
		}
	},
	CONFIRMADA {
		@Override
		public Set<EstadoReserva> siguientes() {
			return EnumSet.of(EN_REVISION, CANCELADA, COMPLETADA);
		}
	},
	EN_REVISION {
		@Override
		public Set<EstadoReserva> siguientes() {
			return EnumSet.of(CONFIRMADA, CANCELADA);
		}
	},
	PAGO_FALLIDO,
	CANCELADA,
	COMPLETADA;

	/** Estados a los que se puede pasar desde este. Los finales no tienen ninguno. */
	public Set<EstadoReserva> siguientes() {
		return EnumSet.noneOf(EstadoReserva.class);
	}

	public boolean puedePasarA(EstadoReserva destino) {
		return siguientes().contains(destino);
	}

	/** La reserva llegó a confirmarse con su cobro, aunque después se haya cancelado o completado. */
	public boolean fueConfirmada() {
		return this == CONFIRMADA || this == EN_REVISION || this == CANCELADA || this == COMPLETADA;
	}
}
