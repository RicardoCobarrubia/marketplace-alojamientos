package co.edu.unbosque.marketplace.controller;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.dto.UsuarioDTO;
import co.edu.unbosque.marketplace.model.Usuario;
import co.edu.unbosque.marketplace.security.JwtUtil;
import co.edu.unbosque.marketplace.service.UsuarioService;
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
	private final JwtUtil jwtUtil;

	public AuthController(UsuarioService usuarioService, JwtUtil jwtUtil) {
		this.usuarioService = usuarioService;
		this.jwtUtil = jwtUtil;
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
		Usuario usuario = usuarioService.iniciarSesion(datos);
		String token = jwtUtil.generarToken(usuario);
		return new AuthResponse(token, "Bearer", jwtUtil.extraerExpiracion(token).orElseThrow(),
				UsuarioDTO.desde(usuario));
	}

	/**
	 * Respuesta del login, como la clase interna AuthResponse de Software 1. El frontend manda
	 * {@code token} en la cabecera {@code Authorization: Bearer <token>} y usa {@code usuario.rol}
	 * para mostrar la vista de su rol.
	 */
	public record AuthResponse(String token, String tipo, Instant expiraEn, UsuarioDTO usuario) {
	}
}
