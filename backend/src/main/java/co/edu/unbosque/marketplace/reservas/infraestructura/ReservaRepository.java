package co.edu.unbosque.marketplace.reservas.infraestructura;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplace.reservas.dominio.Reserva;
import jakarta.persistence.LockModeType;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

	/**
	 * Bloquea la reserva durante la transacción 2 (ADR-002): el flujo de pago y el conciliador
	 * no pueden cambiarla a la vez.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Reserva r where r.id = :id")
	Optional<Reserva> buscarParaActualizar(@Param("id") Long id);

	/** Reservas en PENDIENTE_PAGO creadas antes de {@code limite}: su retención ya venció (MA-60). */
	@Query("""
			select r.id from Reserva r
			where r.estado = co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva.PENDIENTE_PAGO
			  and r.creadoEn < :limite
			order by r.creadoEn""")
	List<Long> buscarPendientesCreadasAntesDe(@Param("limite") Instant limite);

	@Query("select r.id from Reserva r where r.pagoPorVerificar = true order by r.actualizadoEn")
	List<Long> buscarPagosPorVerificar();
}
