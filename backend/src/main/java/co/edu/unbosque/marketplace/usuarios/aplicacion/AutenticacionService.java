package co.edu.unbosque.marketplace.usuarios.aplicacion;

import org.springframework.stereotype.Service;

import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.infraestructura.seguridad.JwtUtil;

/**
 * Casos de uso de inicio de sesión (MA-48) y reactivación (MA-52): valida las credenciales con {@link UsuarioService}
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
		return conToken(usuarioService.iniciarSesion(solicitud));
	}

	/** Reactiva la cuenta desactivada y abre la sesión (MA-52, escenario 3). */
	public AuthResponse reactivar(UsuarioDTO solicitud) {
		return conToken(usuarioService.reactivar(solicitud));
	}

	private AuthResponse conToken(Usuario usuario) {
		String token = jwtUtil.generarToken(usuario);
		return new AuthResponse(token, "Bearer", jwtUtil.extraerExpiracion(token).orElseThrow(),
				UsuarioDTO.desde(usuario));
	}
}
