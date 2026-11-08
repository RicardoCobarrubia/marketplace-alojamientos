package co.edu.unbosque.marketplace.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.dto.AlojamientoDTO;
import co.edu.unbosque.marketplace.model.Alojamiento.EstadoAlojamiento;
import co.edu.unbosque.marketplace.model.Usuario;
import co.edu.unbosque.marketplace.service.AlojamientoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Revisión de alojamientos por el administrador (MA-57). Las rutas /api/admin/** exigen el rol
 * ADMINISTRADOR en SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/alojamientos")
@Tag(name = "Administración de alojamientos")
public class AdminAlojamientoController {

	private final AlojamientoService alojamientoService;

	public AdminAlojamientoController(AlojamientoService alojamientoService) {
		this.alojamientoService = alojamientoService;
	}

	// GET /api/admin/alojamientos?estado=PENDIENTE_REVISION
	@GetMapping
	@Operation(summary = "Consultar todos los alojamientos, o los de un estado")
	public List<AlojamientoDTO> listar(@RequestParam(required = false) EstadoAlojamiento estado) {
		return alojamientoService.listarTodos(estado);
	}

	@PostMapping("/{id}/aprobar")
	@Operation(summary = "Aprobar (PENDIENTE_REVISION → ACTIVA)")
	public AlojamientoDTO aprobar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
		return alojamientoService.aprobar(usuario, id);
	}

	@PostMapping("/{id}/rechazar")
	@Operation(summary = "Rechazar con motivo (PENDIENTE_REVISION → BORRADOR)")
	public AlojamientoDTO rechazar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id,
			@Validated(AlojamientoDTO.Rechazo.class) @RequestBody AlojamientoDTO datos) {
		return alojamientoService.rechazar(usuario, id, datos.motivoRechazo());
	}
}
