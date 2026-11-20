package co.edu.unbosque.marketplace.resenas.infraestructura;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplace.resenas.dominio.Resena;
import jakarta.persistence.LockModeType;

public interface ResenaRepository extends JpaRepository<Resena, Long> {

	boolean existsByReservaId(Long reservaId);

	/** Las visibles de un alojamiento, las más recientes primero (MA-78, escenario 3). */
	List<Resena> findByAlojamientoIdAndOcultaFalseOrderByCreadaEnDescIdDesc(Long alojamientoId);

	/** Para el administrador: todas, o solo las ocultas o solo las visibles. */
	List<Resena> findAllByOrderByCreadaEnDescIdDesc();

	List<Resena> findByOcultaOrderByCreadaEnDescIdDesc(boolean oculta);

	/** La reseña con su fila bloqueada mientras el administrador la modera (MA-79). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Resena r where r.id = :id")
	Optional<Resena> buscarParaModerar(@Param("id") Long id);
}
