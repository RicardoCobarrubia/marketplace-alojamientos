package co.edu.unbosque.marketplace.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.dto.UsuarioResponse;
import co.edu.unbosque.marketplace.model.Usuario;
import co.edu.unbosque.marketplace.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios")
public class UsuarioController {

	private final UsuarioService usuarioService;

	public UsuarioController(UsuarioService usuarioService) {
		this.usuarioService = usuarioService;
	}

	/**
	 * Datos del usuario de la sesión. Siempre responde con el usuario del token, nunca con un id
	 * que mande el cliente, así nadie puede ver el perfil de otro (MA-51, escenario 3).
	 */
	@GetMapping("/me")
	@Operation(summary = "Consultar mis datos")
	public UsuarioResponse obtenerMisDatos(@AuthenticationPrincipal Usuario usuario) {
		return usuarioService.obtener(usuario.getId());
	}
}
