package co.edu.unbosque.marketplace.perfiles.presentacion;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.perfiles.aplicacion.DesactivacionCuentaService;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Estado de la cuenta propia (MA-52). Para reactivarla no hace falta token: se hace con las
 * credenciales en {@code POST /api/auth/reactivacion}.
 */
@RestController
@Tag(name = "Usuarios")
public class CuentaController {

	private final DesactivacionCuentaService desactivacion;

	public CuentaController(DesactivacionCuentaService desactivacion) {
		this.desactivacion = desactivacion;
	}

	@PostMapping("/api/usuarios/me/desactivacion")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAnyRole('HUESPED', 'ANFITRION')")
	@Operation(summary = "Desactivar mi cuenta; mis alojamientos activos quedan en pausa")
	public void desactivar(@AuthenticationPrincipal Usuario usuario) {
		desactivacion.desactivar(usuario.getId());
	}
}
