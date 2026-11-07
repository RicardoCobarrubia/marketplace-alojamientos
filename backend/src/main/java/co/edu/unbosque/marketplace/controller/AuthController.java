package co.edu.unbosque.marketplace.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.dto.LoginRequest;
import co.edu.unbosque.marketplace.dto.RegistroRequest;
import co.edu.unbosque.marketplace.dto.TokenResponse;
import co.edu.unbosque.marketplace.dto.UsuarioResponse;
import co.edu.unbosque.marketplace.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Registro e inicio de sesión. Son las únicas rutas de la API que no piden token. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación")
@SecurityRequirements
public class AuthController {

	private final UsuarioService usuarioService;

	public AuthController(UsuarioService usuarioService) {
		this.usuarioService = usuarioService;
	}

	// POST /api/auth/registro — MA-47
	@PostMapping("/registro")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Registrarse como huésped o anfitrión (MA-47)")
	public UsuarioResponse registrar(@Valid @RequestBody RegistroRequest solicitud) {
		return usuarioService.registrar(solicitud);
	}

	// POST /api/auth/login — MA-48
	@PostMapping("/login")
	@Operation(summary = "Iniciar sesión y obtener el token (MA-48)")
	public TokenResponse iniciarSesion(@Valid @RequestBody LoginRequest solicitud) {
		return usuarioService.iniciarSesion(solicitud);
	}
}
