package co.edu.unbosque.marketplace.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplace.model.Alojamiento;
import co.edu.unbosque.marketplace.model.Alojamiento.EstadoAlojamiento;
import jakarta.persistence.LockModeType;

public interface AlojamientoRepository extends JpaRepository<Alojamiento, Long> {

	List<Alojamiento> findByAnfitrionIdOrderByCreadoEnDesc(Long anfitrionId);

	List<Alojamiento> findByEstadoOrderByActualizadoEnAsc(EstadoAlojamiento estado);

	List<Alojamiento> findAllByOrderByCreadoEnDesc();

	/**
	 * Bloquea la fila del alojamiento hasta terminar la transacción. Lo usan los cambios de
	 * disponibilidad y lo usará la creación de reservas (MA-66): así una reserva no puede entrar
	 * mientras se reduce o elimina el periodo que la contiene.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Alojamiento a where a.id = :id")
	Optional<Alojamiento> buscarParaActualizar(@Param("id") Long id);
}
