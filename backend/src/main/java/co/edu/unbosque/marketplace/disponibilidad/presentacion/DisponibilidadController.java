package co.edu.unbosque.marketplace.disponibilidad.presentacion;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.disponibilidad.aplicacion.DisponibilidadDTO;
import co.edu.unbosque.marketplace.disponibilidad.aplicacion.DisponibilidadService;
import co.edu.unbosque.marketplace.disponibilidad.aplicacion.PeriodoDisponibleDTO;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Disponibilidad de un alojamiento (MA-58, MA-59). */
@RestController
@RequestMapping("/api/alojamientos/{alojamientoId}")
@Tag(name = "Disponibilidad")
public class DisponibilidadController {

	private static final String SOLO_ANFITRION = "hasRole('ANFITRION')";

	private final DisponibilidadService disponibilidadService;

	public DisponibilidadController(DisponibilidadService disponibilidadService) {
		this.disponibilidadService = disponibilidadService;
	}

	// GET /api/alojamientos/{id}/disponibilidad?llegada=2026-12-20&salida=2026-12-23 — MA-59
	@GetMapping("/disponibilidad")
	@Operation(summary = "Consultar si se puede reservar en unas fechas (pública para alojamientos ACTIVA)")
	public DisponibilidadDTO consultar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long alojamientoId,
			@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate llegada,
			@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate salida) {
		return disponibilidadService.consultar(usuario, alojamientoId, llegada, salida);
	}

	// ─────────────────────────────────────────
	// Periodos disponibles — MA-58
	// ─────────────────────────────────────────

	@GetMapping("/periodos")
	@Operation(summary = "Listar los periodos disponibles")
	public List<PeriodoDisponibleDTO> listar(@AuthenticationPrincipal Usuario usuario,
			@PathVariable Long alojamientoId) {
		return disponibilidadService.listarPeriodos(usuario, alojamientoId);
	}

	@PostMapping("/periodos")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(SOLO_ANFITRION)
	@Operation(summary = "Crear un periodo disponible")
	public PeriodoDisponibleDTO crear(@AuthenticationPrincipal Usuario usuario, @PathVariable Long alojamientoId,
			@Valid @RequestBody PeriodoDisponibleDTO datos) {
		return disponibilidadService.crearPeriodo(usuario, alojamientoId, datos);
	}

	@PutMapping("/periodos/{periodoId}")
	@PreAuthorize(SOLO_ANFITRION)
	@Operation(summary = "Modificar un periodo disponible")
	public PeriodoDisponibleDTO editar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long alojamientoId,
			@PathVariable Long periodoId, @Valid @RequestBody PeriodoDisponibleDTO datos) {
		return disponibilidadService.editarPeriodo(usuario, alojamientoId, periodoId, datos);
	}

	@DeleteMapping("/periodos/{periodoId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(SOLO_ANFITRION)
	@Operation(summary = "Eliminar un periodo disponible")
	public void eliminar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long alojamientoId,
			@PathVariable Long periodoId) {
		disponibilidadService.eliminarPeriodo(usuario, alojamientoId, periodoId);
	}
}
