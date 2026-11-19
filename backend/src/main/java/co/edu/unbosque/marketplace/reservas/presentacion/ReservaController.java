package co.edu.unbosque.marketplace.reservas.presentacion;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.reservas.aplicacion.CancelacionDTO;
import co.edu.unbosque.marketplace.reservas.aplicacion.GestionReservasService;
import co.edu.unbosque.marketplace.reservas.aplicacion.ReservaDTO;
import co.edu.unbosque.marketplace.reservas.aplicacion.ReservaService;
import co.edu.unbosque.marketplace.reservas.aplicacion.ReservaTransacciones;
import co.edu.unbosque.marketplace.reservas.aplicacion.RespuestaReserva;
import co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Reservas (MA-66 a MA-72). */
@RestController
@RequestMapping("/api/reservas")
@Tag(name = "Reservas")
public class ReservaController {

	private final ReservaService reservaService;
	private final ReservaTransacciones reservas;
	private final GestionReservasService gestion;

	public ReservaController(ReservaService reservaService, ReservaTransacciones reservas,
			GestionReservasService gestion) {
		this.reservaService = reservaService;
		this.reservas = reservas;
		this.gestion = gestion;
	}

	/**
	 * Responde 201 si quedó CONFIRMADA, 402 si el pago se rechazó, 504 si la pasarela no
	 * respondió, 202 si la misma solicitud sigue en proceso y 409 si las fechas ya no están.
	 */
	@PostMapping
	@PreAuthorize("hasRole('HUESPED')")
	@Operation(summary = "Reservar y pagar (requiere la cabecera Idempotency-Key)")
	public ResponseEntity<String> reservar(@AuthenticationPrincipal Usuario huesped,
			@RequestHeader(name = "Idempotency-Key", required = false) String clave,
			@Valid @RequestBody ReservaDTO solicitud) {
		RespuestaReserva respuesta = reservaService.reservar(huesped, clave, solicitud);
		return ResponseEntity.status(respuesta.codigoHttp())
				.contentType(respuesta.esError() ? MediaType.APPLICATION_PROBLEM_JSON : MediaType.APPLICATION_JSON)
				.body(respuesta.cuerpo());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Ver una reserva (el huésped, el anfitrión del alojamiento o un administrador)")
	public ReservaDTO obtener(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id) {
		return reservas.obtener(usuario, id);
	}

	@GetMapping
	@Operation(summary = "Historial de reservas: las propias (huésped), las de mis alojamientos (anfitrión) o todas"
			+ " (administrador); se puede filtrar por estado")
	public List<ReservaDTO> listar(@AuthenticationPrincipal Usuario usuario,
			@RequestParam(required = false) EstadoReserva estado) {
		return gestion.listar(usuario, estado);
	}

	@PostMapping("/{id}/cancelacion")
	@Operation(summary = "Cancelar una reserva propia confirmada o en revisión, antes de la llegada")
	public ReservaDTO cancelar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long id,
			@Valid @RequestBody CancelacionDTO cancelacion) {
		return gestion.cancelar(usuario, id, cancelacion.motivo());
	}
}
