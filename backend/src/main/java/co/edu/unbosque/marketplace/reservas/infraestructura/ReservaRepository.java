package co.edu.unbosque.marketplace.reservas.infraestructura;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;
import jakarta.persistence.LockModeType;

public interface ReservaRepository extends JpaRepository<Reserva, Long>, JpaSpecificationExecutor<Reserva> {

	/** Las reservas que cumplen el filtro ({@link FiltrosReserva}), con sus servicios en la misma consulta (MA-70). */
	@Override
	@EntityGraph(attributePaths = "servicios")
	List<Reserva> findAll(Specification<Reserva> filtro, Sort orden);

	/**
	 * Bloquea la reserva durante la transacción 2 (ADR-002): el flujo de pago y el conciliador
	 * no pueden cambiarla a la vez.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Reserva r where r.id = :id")
	Optional<Reserva> buscarParaActualizar(@Param("id") Long id);

	/** Reservas en un estado creadas antes de {@code limite}; con PENDIENTE_PAGO, su retención ya venció (MA-60). */
	@Query("""
			select r.id from Reserva r
			where r.estado = :estado and r.creadoEn < :limite
			order by r.creadoEn""")
	List<Long> buscarPorEstadoCreadasAntesDe(@Param("estado") EstadoReserva estado, @Param("limite") Instant limite);

	/** Reservas en un estado cuya fecha de salida ya llegó (MA-71, escenario 3). */
	@Query("""
			select r.id from Reserva r
			where r.estado = :estado and r.fechaSalida <= :hoy
			order by r.fechaSalida""")
	List<Long> buscarPorEstadoConSalidaHasta(@Param("estado") EstadoReserva estado, @Param("hoy") LocalDate hoy);

	/** Cancelaciones cuya devolución la pasarela todavía no aceptó (MA-74, escenario 3). */
	@Query("select r.id from Reserva r where r.devolucionPendiente = true order by r.canceladaEn")
	List<Long> buscarDevolucionesPendientes();

	@Query("select r.id from Reserva r where r.pagoPorVerificar = true order by r.actualizadoEn")
	List<Long> buscarPagosPorVerificar();
}
