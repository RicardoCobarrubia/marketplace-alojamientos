package co.edu.unbosque.marketplace.notificaciones.infraestructura;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplace.notificaciones.dominio.Notificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

	/** Las notificaciones de un usuario, las más recientes primero (MA-76). */
	List<Notificacion> findByUsuarioIdOrderByCreadaEnDescIdDesc(Long usuarioId);

	/** Solo la encuentra su dueño: la de otro usuario no existe para él (MA-76, escenario 2). */
	Optional<Notificacion> findByIdAndUsuarioId(Long id, Long usuarioId);

	boolean existsByEventoIdAndUsuarioId(Long eventoId, Long usuarioId);

	/** Las notificaciones con el correo pendiente, las más antiguas primero. */
	@Query(value = "SELECT id FROM notificacion WHERE correo_estado = 'PENDIENTE' ORDER BY id LIMIT :limite",
			nativeQuery = true)
	List<Long> idsConCorreoPendiente(@Param("limite") int limite);

	/** Toma una con el correo pendiente y su fila bloqueada; si otra réplica la tiene, la salta. */
	@Query(value = "SELECT * FROM notificacion WHERE id = :id AND correo_estado = 'PENDIENTE' FOR UPDATE SKIP LOCKED",
			nativeQuery = true)
	Optional<Notificacion> tomarConCorreoPendiente(@Param("id") Long id);
}
