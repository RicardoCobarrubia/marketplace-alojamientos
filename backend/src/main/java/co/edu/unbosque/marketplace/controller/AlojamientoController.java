package co.edu.unbosque.marketplace.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.dto.AlojamientoDTO;
import co.edu.unbosque.marketplace.dto.ServicioAdicionalDTO;
import co.edu.unbosque.marketplace.model.Usuario;
import co.edu.unbosque.marketplace.service.AlojamientoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Alojamientos del anfitrión (MA-53 a MA-56). */
@RestController
@RequestMapping("/api/alojamientos")
@Tag(name = "Alojamientos")
public class AlojamientoController {

	private static final String SOLO_ANFITRION = "hasRole('ANFITRION')";

	private final AlojamientoService alojamientoService;

	public AlojamientoController(AlojamientoService alojamientoService) {
		this.alojamientoService = alojamientoService;
	}

	// POST /api/alojamientos — MA-53
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(SOLO_ANFITRION)
	@Operation(summary = "Registrar un alojamiento (queda en BORRADOR)")
	public AlojamientoDTO crear(@AuthenticationPrincipal Usuario usuario,
			@Validated(AlojamientoDTO.Datos.class) @RequestBody AlojamientoDTO solicitud) {
		return alojamientoService.crear(usuario, solicitud);
	}

	// GET /api/alojamientos/mios — MA-56
	@GetMapping("/mios")
	@PreAuthorize(SOLO_ANFITRION)
	@Operation(summary = "Consultar mis alojamientos y su estado")
	public List<AlojamientoDTO> listarMios(@AuthenticationPrincipal Usuario usuario) {
		return alojamientoService.listarDelAnfitrion(usuario);
	}

	// GET /api/alojamientos/{id} — detalle
	@GetMapping("/{id}")
	@Operation(summary = "Ver el detalle de un alojamiento")
	public AlojamientoDTO obtener(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
		return alojamientoService.obtener(usuario, id);
	}

	// PUT /api/alojamientos/{id} — MA-54
	@PutMapping("/{id}")
	@PreAuthorize("hasAnyRole('ANFITRION', 'ADMINISTRADOR')")
	@Operation(summary = "Editar un alojamiento (dueño o administrador)")
	public AlojamientoDTO editar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id,
			@Validated(AlojamientoDTO.Datos.class) @RequestBody AlojamientoDTO solicitud) {
		return alojamientoService.editar(usuario, id, solicitud);
	}

	// ─────────────────────────────────────────
	// Estados — MA-56
	// ─────────────────────────────────────────

	@PostMapping("/{id}/enviar-revision")
	@PreAuthorize(SOLO_ANFITRION)
	@Operation(summary = "Enviar a revisión (BORRADOR → PENDIENTE_REVISION)")
	public AlojamientoDTO enviarARevision(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
		return alojamientoService.enviarARevision(usuario, id);
	}

	@PostMapping("/{id}/pausar")
	@PreAuthorize(SOLO_ANFITRION)
	@Operation(summary = "Pausar (ACTIVA → PAUSADA)")
	public AlojamientoDTO pausar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
		return alojamientoService.pausar(usuario, id);
	}

	@PostMapping("/{id}/reanudar")
	@PreAuthorize(SOLO_ANFITRION)
	@Operation(summary = "Reanudar (PAUSADA → ACTIVA)")
	public AlojamientoDTO reanudar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
		return alojamientoService.reanudar(usuario, id);
	}

	// ─────────────────────────────────────────
	// Servicios adicionales — MA-55
	// ─────────────────────────────────────────

	@PostMapping("/{id}/servicios-adicionales")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(SOLO_ANFITRION)
	@Operation(summary = "Agregar un servicio adicional")
	public ServicioAdicionalDTO agregarServicioAdicional(@AuthenticationPrincipal Usuario usuario,
			@PathVariable Long id, @Valid @RequestBody ServicioAdicionalDTO solicitud) {
		return alojamientoService.agregarServicioAdicional(usuario, id, solicitud);
	}

	@PutMapping("/{id}/servicios-adicionales/{servicioId}")
	@PreAuthorize(SOLO_ANFITRION)
	@Operation(summary = "Editar un servicio adicional")
	public ServicioAdicionalDTO editarServicioAdicional(@AuthenticationPrincipal Usuario usuario,
			@PathVariable Long id, @PathVariable Long servicioId,
			@Valid @RequestBody ServicioAdicionalDTO solicitud) {
		return alojamientoService.editarServicioAdicional(usuario, id, servicioId, solicitud);
	}

	@DeleteMapping("/{id}/servicios-adicionales/{servicioId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(SOLO_ANFITRION)
	@Operation(summary = "Retirar un servicio adicional")
	public void retirarServicioAdicional(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id,
			@PathVariable Long servicioId) {
		alojamientoService.retirarServicioAdicional(usuario, id, servicioId);
	}
}
