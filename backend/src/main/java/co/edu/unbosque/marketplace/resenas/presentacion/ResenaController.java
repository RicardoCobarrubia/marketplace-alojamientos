package co.edu.unbosque.marketplace.resenas.presentacion;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.resenas.aplicacion.NuevaResenaDTO;
import co.edu.unbosque.marketplace.resenas.aplicacion.ResenaDTO;
import co.edu.unbosque.marketplace.resenas.aplicacion.ResenaService;
import co.edu.unbosque.marketplace.resenas.aplicacion.ResenasAlojamientoDTO;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Reseñas (MA-77, MA-78). No hay forma de editar una reseña publicada (MA-77, escenario 3). */
@RestController
@Tag(name = "Reseñas")
public class ResenaController {

	private final ResenaService resenas;

	public ResenaController(ResenaService resenas) {
		this.resenas = resenas;
	}

	@PostMapping("/api/reservas/{id}/resena")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Reseñar una estadía completada, dentro de los 30 días siguientes a la salida")
	public ResenaDTO publicar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id,
			@Valid @RequestBody NuevaResenaDTO solicitud) {
		return resenas.publicar(usuario, id, solicitud.calificacion(), solicitud.comentario());
	}

	@GetMapping("/api/alojamientos/{id}/resenas")
	@Operation(summary = "Reseñas visibles de un alojamiento publicado, con su calificación promedio (pública)")
	public ResenasAlojamientoDTO delAlojamiento(@PathVariable Long id) {
		return resenas.delAlojamiento(id);
	}
}
