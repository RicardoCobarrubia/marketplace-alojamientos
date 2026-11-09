package co.edu.unbosque.marketplace.usuarios.aplicacion;

import org.springframework.stereotype.Service;

import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.infraestructura.seguridad.JwtUtil;

/**
 * Caso de uso de inicio de sesión (MA-48): valida las credenciales con {@link UsuarioService}
 * y emite el token. Así el controlador no depende de la infraestructura de seguridad.
 */
@Service
public class AutenticacionService {

	private final UsuarioService usuarioService;
	private final JwtUtil jwtUtil;

	public AutenticacionService(UsuarioService usuarioService, JwtUtil jwtUtil) {
		this.usuarioService = usuarioService;
		this.jwtUtil = jwtUtil;
	}

	public AuthResponse iniciarSesion(UsuarioDTO solicitud) {
		Usuario usuario = usuarioService.iniciarSesion(solicitud);
		String token = jwtUtil.generarToken(usuario);
		return new AuthResponse(token, "Bearer", jwtUtil.extraerExpiracion(token).orElseThrow(),
				UsuarioDTO.desde(usuario));
	}
}
