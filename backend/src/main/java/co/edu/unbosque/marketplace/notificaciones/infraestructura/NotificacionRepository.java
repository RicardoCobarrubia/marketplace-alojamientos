package co.edu.unbosque.marketplace.notificaciones.infraestructura;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplace.notificaciones.dominio.Notificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

	/** Las notificaciones de un usuario, las más recientes primero (MA-76). */
	List<Notificacion> findByUsuarioIdOrderByCreadaEnDescIdDesc(Long usuarioId);

	/** Solo la encuentra su dueño: la de otro usuario no existe para él (MA-76, escenario 2). */
	Optional<Notificacion> findByIdAndUsuarioId(Long id, Long usuarioId);

	boolean existsByEventoIdAndUsuarioId(Long eventoId, Long usuarioId);
}
