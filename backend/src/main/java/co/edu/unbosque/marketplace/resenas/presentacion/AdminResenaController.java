package co.edu.unbosque.marketplace.resenas.presentacion;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.resenas.aplicacion.MotivoOcultamientoDTO;
import co.edu.unbosque.marketplace.resenas.aplicacion.ResenaDTO;
import co.edu.unbosque.marketplace.resenas.aplicacion.ResenaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Moderación de reseñas (MA-79). Las rutas {@code /api/admin/**} exigen el rol ADMINISTRADOR. */
@RestController
@RequestMapping("/api/admin/resenas")
@Tag(name = "Moderación")
public class AdminResenaController {

	private final ResenaService resenas;

	public AdminResenaController(ResenaService resenas) {
		this.resenas = resenas;
	}

	@GetMapping
	@Operation(summary = "Todas las reseñas, o solo las ocultas (oculta=true) o las visibles (oculta=false)")
	public List<ResenaDTO> listar(@RequestParam(required = false) Boolean oculta) {
		return resenas.listar(oculta);
	}

	@PostMapping("/{id}/ocultamiento")
	@Operation(summary = "Ocultar una reseña con motivo; deja de mostrarse y de contar en el promedio")
	public ResenaDTO ocultar(@PathVariable Long id, @Valid @RequestBody MotivoOcultamientoDTO solicitud) {
		return resenas.ocultar(id, solicitud.motivo());
	}

	@DeleteMapping("/{id}/ocultamiento")
	@Operation(summary = "Volver a mostrar una reseña oculta")
	public ResenaDTO mostrar(@PathVariable Long id) {
		return resenas.mostrar(id);
	}
}
