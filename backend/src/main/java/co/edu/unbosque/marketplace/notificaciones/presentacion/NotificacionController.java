package co.edu.unbosque.marketplace.notificaciones.presentacion;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.notificaciones.aplicacion.NotificacionDTO;
import co.edu.unbosque.marketplace.notificaciones.aplicacion.NotificacionService;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Mis notificaciones (MA-76). */
@RestController
@RequestMapping("/api/notificaciones")
@Tag(name = "Notificaciones")
public class NotificacionController {

	private final NotificacionService notificaciones;

	public NotificacionController(NotificacionService notificaciones) {
		this.notificaciones = notificaciones;
	}

	@GetMapping
	@Operation(summary = "Mis notificaciones, las más recientes primero")
	public List<NotificacionDTO> listar(@AuthenticationPrincipal Usuario usuario) {
		return notificaciones.listar(usuario);
	}

	@PostMapping("/{id}/lectura")
	@Operation(summary = "Marcar una notificación propia como leída")
	public NotificacionDTO marcarLeida(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
		return notificaciones.marcarLeida(usuario, id);
	}
}
