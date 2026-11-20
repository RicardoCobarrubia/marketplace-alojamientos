package co.edu.unbosque.marketplace.alojamientos.infraestructura;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento.EstadoAlojamiento;
import jakarta.persistence.LockModeType;

public interface AlojamientoRepository extends JpaRepository<Alojamiento, Long> {

	List<Alojamiento> findByAnfitrionIdOrderByCreadoEnDesc(Long anfitrionId);

	List<Alojamiento> findByEstadoOrderByActualizadoEnAsc(EstadoAlojamiento estado);

	/** Los alojamientos de un anfitrión en un estado, por ejemplo los ACTIVA al restringirlo (MA-81). */
	List<Alojamiento> findByAnfitrionIdAndEstado(Long anfitrionId, EstadoAlojamiento estado);

	List<Alojamiento> findAllByOrderByCreadoEnDesc();

	/**
	 * El estado actual del alojamiento, leído de la base de datos con bloqueo compartido: espera
	 * a que termine un bloqueo en curso (MA-80) y le impide empezar hasta terminar la transacción.
	 */
	@Query(value = "SELECT estado FROM alojamiento WHERE id = :id FOR SHARE", nativeQuery = true)
	String estadoConBloqueoCompartido(@Param("id") Long id);

	/**
	 * Bloquea la fila del alojamiento hasta terminar la transacción. Lo usan los cambios de
	 * disponibilidad y lo usará la creación de reservas (MA-66): así una reserva no puede entrar
	 * mientras se reduce o elimina el periodo que la contiene.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Alojamiento a where a.id = :id")
	Optional<Alojamiento> buscarParaActualizar(@Param("id") Long id);

	/**
	 * Bloqueo compartido para reservar (MA-66): varias reservas del mismo alojamiento avanzan a
	 * la vez, porque la restricción de exclusión decide cuál gana (ADR-003), pero ninguna puede
	 * entrar mientras el anfitrión reduce o elimina un periodo ({@link #buscarParaActualizar}).
	 */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select a from Alojamiento a where a.id = :id")
	Optional<Alojamiento> buscarParaReservar(@Param("id") Long id);
}
