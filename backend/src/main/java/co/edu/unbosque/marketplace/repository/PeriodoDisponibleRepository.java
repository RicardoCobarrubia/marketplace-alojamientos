package co.edu.unbosque.marketplace.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplace.model.PeriodoDisponible;

public interface PeriodoDisponibleRepository extends JpaRepository<PeriodoDisponible, Long> {

	List<PeriodoDisponible> findByAlojamientoIdOrderByFechaInicioAsc(Long alojamientoId);

	/** Otro periodo del mismo alojamiento que se cruza con [inicio, fin). */
	@Query("""
			select count(p) > 0 from PeriodoDisponible p
			where p.alojamiento.id = :alojamientoId and p.id <> :excluirId
			  and p.fechaInicio < :fin and p.fechaFin > :inicio""")
	boolean existeOtroQueSeCruza(@Param("alojamientoId") Long alojamientoId, @Param("excluirId") Long excluirId,
			@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);

	/** Algún periodo contiene completa la estadía [llegada, salida). */
	@Query("""
			select count(p) > 0 from PeriodoDisponible p
			where p.alojamiento.id = :alojamientoId
			  and p.fechaInicio <= :llegada and p.fechaFin >= :salida""")
	boolean algunoContiene(@Param("alojamientoId") Long alojamientoId, @Param("llegada") LocalDate llegada,
			@Param("salida") LocalDate salida);
}
