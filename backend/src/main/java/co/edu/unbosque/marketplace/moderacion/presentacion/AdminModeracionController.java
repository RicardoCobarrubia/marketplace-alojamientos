package co.edu.unbosque.marketplace.moderacion.presentacion;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.moderacion.aplicacion.ModeracionService;
import co.edu.unbosque.marketplace.moderacion.aplicacion.MotivoDTO;
import co.edu.unbosque.marketplace.moderacion.aplicacion.ResultadoModeracionDTO;
import co.edu.unbosque.marketplace.reservas.aplicacion.CancelacionService;
import co.edu.unbosque.marketplace.reservas.aplicacion.ReservaDTO;
import co.edu.unbosque.marketplace.reservas.aplicacion.RevisionReservasService;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Moderación (MA-80 a MA-82). Las rutas {@code /api/admin/**} exigen el rol ADMINISTRADOR
 * (SecurityConfig); cualquier otro rol recibe 403 y el intento queda auditado.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Moderación")
public class AdminModeracionController {

	private final ModeracionService moderacion;
	private final RevisionReservasService revisionReservas;
	private final CancelacionService cancelaciones;

	public AdminModeracionController(ModeracionService moderacion, RevisionReservasService revisionReservas,
			CancelacionService cancelaciones) {
		this.moderacion = moderacion;
		this.revisionReservas = revisionReservas;
		this.cancelaciones = cancelaciones;
	}

	@PostMapping("/alojamientos/{id}/bloqueo")
	@Operation(summary = "Bloquear un alojamiento con motivo; sus reservas futuras pasan a EN_REVISION")
	public ResultadoModeracionDTO bloquear(@PathVariable Long id, @Valid @RequestBody MotivoDTO solicitud) {
		return moderacion.bloquearAlojamiento(id, solicitud.motivo());
	}

	@PostMapping("/alojamientos/{id}/desbloqueo")
	@Operation(summary = "Desbloquear un alojamiento; queda PAUSADA")
	public ResultadoModeracionDTO desbloquear(@PathVariable Long id) {
		return moderacion.desbloquearAlojamiento(id);
	}

	@PostMapping("/usuarios/{id}/restriccion")
	@Operation(summary = "Restringir un usuario con motivo: cierra sus sesiones, pausa sus alojamientos y pone"
			+ " sus reservas futuras EN_REVISION")
	public ResultadoModeracionDTO restringir(@PathVariable Long id, @Valid @RequestBody MotivoDTO solicitud) {
		return moderacion.restringirUsuario(id, solicitud.motivo());
	}

	@DeleteMapping("/usuarios/{id}/restriccion")
	@Operation(summary = "Levantar la restricción de un usuario")
	public ResultadoModeracionDTO levantarRestriccion(@PathVariable Long id) {
		return moderacion.levantarRestriccion(id);
	}

	@GetMapping("/reservas/en-revision")
	@Operation(summary = "Reservas EN_REVISION")
	public List<ReservaDTO> enRevision() {
		return revisionReservas.enRevision();
	}

	@PostMapping("/reservas/{id}/mantener")
	@Operation(summary = "Mantener una reserva EN_REVISION: vuelve a CONFIRMADA")
	public ReservaDTO mantener(@PathVariable Long id) {
		return revisionReservas.mantener(id);
	}

	@PostMapping("/reservas/{id}/cancelacion")
	@Operation(summary = "Cancelar una reserva como administrador, con devolución del 100 %")
	public ReservaDTO cancelar(@AuthenticationPrincipal Usuario administrador, @PathVariable Long id,
			@Valid @RequestBody MotivoDTO solicitud) {
		return cancelaciones.cancelar(administrador, id, solicitud.motivo());
	}
}
