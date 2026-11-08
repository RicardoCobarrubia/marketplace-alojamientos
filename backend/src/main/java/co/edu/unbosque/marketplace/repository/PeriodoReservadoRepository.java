package co.edu.unbosque.marketplace.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplace.model.PeriodoReservado;

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
}
