package co.edu.unbosque.marketplace.usuarios.presentacion;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.usuarios.aplicacion.AuthResponse;
import co.edu.unbosque.marketplace.usuarios.aplicacion.AutenticacionService;
import co.edu.unbosque.marketplace.usuarios.aplicacion.UsuarioDTO;
import co.edu.unbosque.marketplace.usuarios.aplicacion.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Registro e inicio de sesión. Son las únicas rutas de la API que no piden token. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación")
@SecurityRequirements
public class AuthController {

	private final UsuarioService usuarioService;
	private final AutenticacionService autenticacionService;

	public AuthController(UsuarioService usuarioService, AutenticacionService autenticacionService) {
		this.usuarioService = usuarioService;
		this.autenticacionService = autenticacionService;
	}

	// POST /api/auth/registro — MA-47
	@PostMapping("/registro")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Registrarse como huésped o anfitrión (MA-47)")
	public UsuarioDTO registrar(@Validated(UsuarioDTO.Registro.class) @RequestBody UsuarioDTO datos) {
		return usuarioService.registrar(datos);
	}

	// POST /api/auth/login — MA-48
	@PostMapping("/login")
	@Operation(summary = "Iniciar sesión y obtener el token (MA-48)")
	public AuthResponse login(@Validated(UsuarioDTO.Login.class) @RequestBody UsuarioDTO datos) {
		return autenticacionService.iniciarSesion(datos);
	}
}
