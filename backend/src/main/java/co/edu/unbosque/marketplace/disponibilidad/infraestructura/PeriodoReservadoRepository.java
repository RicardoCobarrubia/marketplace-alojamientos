package co.edu.unbosque.marketplace.disponibilidad.infraestructura;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplace.disponibilidad.dominio.PeriodoReservado;

public interface PeriodoReservadoRepository extends JpaRepository<PeriodoReservado, Long> {

	/**
	 * Reservas que todavía ocupan fechas dentro de [desde, hasta): confirmadas, en revisión o
	 * retenidas mientras se paga sin haber vencido (MA-59, escenario 2).
	 */
	@Query("""
			select p from PeriodoReservado p
			where p.alojamiento.id = :alojamientoId
			  and p.liberadoEn is null
			  and (p.retenidoHasta is null or p.retenidoHasta > :ahora)
			  and p.fechaLlegada < :hasta and p.fechaSalida > :desde""")
	List<PeriodoReservado> buscarVigentesQueSeCruzan(@Param("alojamientoId") Long alojamientoId,
			@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta, @Param("ahora") Instant ahora);

	/** Las fechas retenidas o bloqueadas de una reserva (MA-60). */
	Optional<PeriodoReservado> findByReservaId(Long reservaId);

	/**
	 * Libera las retenciones vencidas que se cruzan con [desde, hasta) antes de retener esas
	 * fechas para otra reserva (MA-60, escenario 2). La restricción de exclusión solo ignora las
	 * filas liberadas, así que una retención vencida sin liberar seguiría bloqueando.
	 */
	@Modifying(flushAutomatically = true)
	@Query("""
			update PeriodoReservado p set p.liberadoEn = :ahora
			where p.alojamiento.id = :alojamientoId
			  and p.liberadoEn is null
			  and p.retenidoHasta is not null and p.retenidoHasta <= :ahora
			  and p.fechaLlegada < :hasta and p.fechaSalida > :desde""")
	int liberarRetencionesVencidas(@Param("alojamientoId") Long alojamientoId, @Param("desde") LocalDate desde,
			@Param("hasta") LocalDate hasta, @Param("ahora") Instant ahora);
}
