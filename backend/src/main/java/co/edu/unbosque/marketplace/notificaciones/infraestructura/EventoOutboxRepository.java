package co.edu.unbosque.marketplace.notificaciones.infraestructura;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplace.notificaciones.dominio.EventoOutbox;

public interface EventoOutboxRepository extends JpaRepository<EventoOutbox, Long> {

	List<EventoOutbox> findByAgregadoAndAgregadoIdOrderByIdAsc(String agregado, Long agregadoId);

	/** Los eventos pendientes más antiguos primero (ADR-007). */
	@Query(value = "SELECT id FROM evento_outbox WHERE estado = 'PENDIENTE' ORDER BY id LIMIT :limite",
			nativeQuery = true)
	List<Long> idsPendientes(@Param("limite") int limite);

	/**
	 * Toma un evento pendiente con su fila bloqueada. Si otra réplica ya lo está procesando, lo
	 * salta en vez de esperar: dos procesadores a la vez no entregan el mismo evento.
	 */
	@Query(value = "SELECT * FROM evento_outbox WHERE id = :id AND estado = 'PENDIENTE' FOR UPDATE SKIP LOCKED",
			nativeQuery = true)
	Optional<EventoOutbox> tomarPendiente(@Param("id") Long id);

	/**
	 * Suma un intento fallido en una sola sentencia: espera a que termine quien tenga la fila y
	 * solo lo cuenta si el evento sigue pendiente. Al llegar al máximo queda FALLIDO.
	 */
	@Modifying
	@Query(value = """
			UPDATE evento_outbox
			SET intentos = intentos + 1,
			    ultimo_error = :error,
			    estado = CASE WHEN intentos + 1 >= :maximo THEN 'FALLIDO' ELSE estado END
			WHERE id = :id AND estado = 'PENDIENTE'""", nativeQuery = true)
	int registrarFallo(@Param("id") Long id, @Param("error") String error, @Param("maximo") int maximo);
}
