package co.edu.unbosque.marketplace.notificaciones.aplicacion;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.comun.excepcion.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.notificaciones.dominio.Notificacion;
import co.edu.unbosque.marketplace.notificaciones.infraestructura.NotificacionRepository;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;

/** Consultar las notificaciones propias (MA-76). */
@Service
public class NotificacionService {

	private final NotificacionRepository notificaciones;
	private final Clock reloj;

	public NotificacionService(NotificacionRepository notificaciones, Clock reloj) {
		this.notificaciones = notificaciones;
		this.reloj = reloj;
	}

	/** Solo las del usuario, las más recientes primero; sin notificaciones, una lista vacía. */
	@Transactional(readOnly = true)
	public List<NotificacionDTO> listar(Usuario usuario) {
		return notificaciones.findByUsuarioIdOrderByCreadaEnDescIdDesc(usuario.getId()).stream()
				.map(NotificacionDTO::desde).toList();
	}

	/**
	 * Marca como leída una notificación propia; la de otro usuario no existe para él. Si ya estaba
	 * leída conserva la primera fecha.
	 */
	@Transactional
	public NotificacionDTO marcarLeida(Usuario usuario, Long notificacionId) {
		notificaciones.marcarLeida(notificacionId, usuario.getId(), reloj.instant());
		Notificacion notificacion = notificaciones.findByIdAndUsuarioId(notificacionId, usuario.getId())
				.orElseThrow(() -> new RecursoNoEncontradoException("La notificación no existe."));
		return NotificacionDTO.desde(notificacion);
	}
}
