package co.edu.unbosque.marketplace.notificaciones.infraestructura;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplace.notificaciones.dominio.Notificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

	/** Las notificaciones de un usuario, las más recientes primero (MA-76). */
	List<Notificacion> findByUsuarioIdOrderByCreadaEnDescIdDesc(Long usuarioId);

	/** Solo la encuentra su dueño: la de otro usuario no existe para él (MA-76, escenario 2). */
	Optional<Notificacion> findByIdAndUsuarioId(Long id, Long usuarioId);

	boolean existsByEventoIdAndUsuarioId(Long eventoId, Long usuarioId);

	/**
	 * Marca como leída una notificación propia solo si aún no lo estaba, en la base de datos: dos
	 * lecturas a la vez conservan la primera fecha y no tocan el estado del correo.
	 */
	@Modifying(clearAutomatically = true)
	@Query("""
			update Notificacion n set n.leidaEn = :ahora
			where n.id = :id and n.usuarioId = :usuarioId and n.leidaEn is null""")
	int marcarLeida(@Param("id") Long id, @Param("usuarioId") Long usuarioId, @Param("ahora") Instant ahora);

	/** Las notificaciones con el correo pendiente, las más antiguas primero. */
	@Query(value = "SELECT id FROM notificacion WHERE correo_estado = 'PENDIENTE' ORDER BY id LIMIT :limite",
			nativeQuery = true)
	List<Long> idsConCorreoPendiente(@Param("limite") int limite);

	/** Toma una con el correo pendiente y su fila bloqueada; si otra réplica la tiene, la salta. */
	@Query(value = "SELECT * FROM notificacion WHERE id = :id AND correo_estado = 'PENDIENTE' FOR UPDATE SKIP LOCKED",
			nativeQuery = true)
	Optional<Notificacion> tomarConCorreoPendiente(@Param("id") Long id);

	/**
	 * Suma un intento fallido del correo en una sola sentencia: espera a que termine quien tenga
	 * la fila y solo lo cuenta si el correo sigue pendiente. Al llegar al máximo queda FALLIDO.
	 */
	@Modifying
	@Query(value = """
			UPDATE notificacion
			SET correo_intentos = correo_intentos + 1,
			    correo_estado = CASE WHEN correo_intentos + 1 >= :maximo THEN 'FALLIDO' ELSE correo_estado END
			WHERE id = :id AND correo_estado = 'PENDIENTE'""", nativeQuery = true)
	int registrarFalloDeCorreo(@Param("id") Long id, @Param("maximo") int maximo);
}
