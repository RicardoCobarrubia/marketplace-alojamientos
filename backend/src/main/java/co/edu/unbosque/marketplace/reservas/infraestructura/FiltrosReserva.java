package co.edu.unbosque.marketplace.reservas.infraestructura;

import org.springframework.data.jpa.domain.Specification;

import co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;

/** Filtros para consultar reservas con {@link ReservaRepository#findAll(Specification, org.springframework.data.domain.Sort)} (MA-70). */
public final class FiltrosReserva {

	private FiltrosReserva() {
	}

	/** Todas las reservas; con {@code estado}, solo las de ese estado. */
	public static Specification<Reserva> enEstado(EstadoReserva estado) {
		return (reserva, consulta, criterio) -> estado == null
				? criterio.conjunction()
				: criterio.equal(reserva.get("estado"), estado);
	}

	public static Specification<Reserva> delHuesped(Long huespedId) {
		return (reserva, consulta, criterio) -> criterio.equal(reserva.get("huesped").get("id"), huespedId);
	}

	/** Las reservas de los alojamientos de un anfitrión. */
	public static Specification<Reserva> delAnfitrion(Long anfitrionId) {
		return (reserva, consulta, criterio) -> criterio.equal(
				reserva.get("alojamiento").get("anfitrion").get("id"), anfitrionId);
	}
}
