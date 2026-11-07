package co.edu.unbosque.marketplace.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.dto.LoginRequest;
import co.edu.unbosque.marketplace.dto.RegistroRequest;
import co.edu.unbosque.marketplace.dto.TokenResponse;
import co.edu.unbosque.marketplace.dto.UsuarioResponse;
import co.edu.unbosque.marketplace.exception.CredencialesInvalidasException;
import co.edu.unbosque.marketplace.exception.CuentaBloqueadaTemporalmenteException;
import co.edu.unbosque.marketplace.exception.CuentaDesactivadaException;
import co.edu.unbosque.marketplace.exception.CuentaRestringidaException;
import co.edu.unbosque.marketplace.exception.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.exception.ReglaNegocioException;
import co.edu.unbosque.marketplace.exception.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.model.Usuario;
import co.edu.unbosque.marketplace.repository.UsuarioRepository;
import co.edu.unbosque.marketplace.security.JwtUtil;

/**
 * Registro, inicio de sesión y consulta de usuarios (MA-47, MA-48).
 * <p>
 * El login usa el {@code AuthenticationManager} de Spring Security, como en Software 1, pero
 * aquí y no en el controlador, y responde con excepciones en lugar de códigos numéricos.
 */
@Service
public class UsuarioService {

	private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);
	static final String CORREO_DUPLICADO = "CORREO_YA_REGISTRADO";

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtUtil jwtUtil;
	private final Clock reloj;
	private final int intentosMaximos;
	private final Duration duracionBloqueo;

	public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, JwtUtil jwtUtil, Clock reloj,
			@Value("${marketplace.seguridad.intentos-maximos}") int intentosMaximos,
			@Value("${marketplace.seguridad.duracion-bloqueo}") Duration duracionBloqueo) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.jwtUtil = jwtUtil;
		this.reloj = reloj;
		this.intentosMaximos = intentosMaximos;
		this.duracionBloqueo = duracionBloqueo;
	}

	// ─────────────────────────────────────────
	// Registro — MA-47
	// ─────────────────────────────────────────

	/**
	 * Crea la cuenta con el rol elegido. Rechaza el rol Administrador y los correos ya registrados
	 * (escenario 2). La contraseña se guarda solo como hash BCrypt (escenario 3).
	 */
	@Transactional
	public UsuarioResponse registrar(RegistroRequest solicitud) {
		if (!solicitud.rol().sePuedeElegirAlRegistrarse()) {
			throw new SolicitudInvalidaException("ROL_NO_PERMITIDO",
					"Solo puedes registrarte como huésped o anfitrión.");
		}
		String email = Usuario.normalizarEmail(solicitud.email());
		if (usuarioRepository.existsByEmail(email)) {
			throw correoDuplicado();
		}

		Usuario usuario = new Usuario(solicitud.nombre(), email, solicitud.telefono(),
				passwordEncoder.encode(solicitud.password()), solicitud.rol(), reloj.instant());
		try {
			usuario = usuarioRepository.saveAndFlush(usuario);
		} catch (DataIntegrityViolationException e) {
			// Dos registros simultáneos con el mismo correo: la restricción UNIQUE gana
			throw correoDuplicado();
		}
		log.info("Usuario registrado (usuario {}, rol {})", usuario.getId(), usuario.getRol());
		return UsuarioResponse.desde(usuario);
	}

	// ─────────────────────────────────────────
	// Inicio de sesión — MA-48
	// ─────────────────────────────────────────

	/**
	 * Valida las credenciales con el {@code AuthenticationManager} y devuelve el token.
	 * <ul>
	 * <li>Correo o contraseña incorrectos: el mismo error en los dos casos (escenario 1).</li>
	 * <li>5 intentos fallidos seguidos: bloqueo temporal de 15 minutos.</li>
	 * <li>Cuenta restringida: no entra; desactivada: solo puede reactivarse (escenario 2).</li>
	 * </ul>
	 * El intento fallido se guarda aunque se lance la excepción, por eso {@code noRollbackFor}.
	 */
	@Transactional(noRollbackFor = CredencialesInvalidasException.class)
	public TokenResponse iniciarSesion(LoginRequest solicitud) {
		Instant ahora = reloj.instant();
		String email = Usuario.normalizarEmail(solicitud.email());
		Optional<Usuario> registrado = usuarioRepository.findByEmail(email);

		if (registrado.isPresent() && registrado.get().estaBloqueadoTemporalmente(ahora)) {
			throw new CuentaBloqueadaTemporalmenteException();
		}

		Authentication autenticacion;
		try {
			autenticacion = authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(email, solicitud.password()));
		} catch (BadCredentialsException e) {
			registrado.ifPresent(usuario -> {
				usuario.registrarIntentoFallido(ahora, intentosMaximos, duracionBloqueo);
				log.info("Inicio de sesión fallido (usuario {})", usuario.getId());
			});
			throw new CredencialesInvalidasException();
		} catch (LockedException e) {
			throw new CuentaRestringidaException();
		} catch (DisabledException e) {
			throw new CuentaDesactivadaException();
		}

		Usuario usuario = (Usuario) autenticacion.getPrincipal();
		usuario.registrarInicioSesionExitoso();
		usuarioRepository.save(usuario);

		String token = jwtUtil.generarToken(usuario);
		log.info("Inicio de sesión exitoso (usuario {}, rol {})", usuario.getId(), usuario.getRol());
		return TokenResponse.bearer(token, jwtUtil.extraerExpiracion(token).orElseThrow(),
				UsuarioResponse.desde(usuario));
	}

	// ─────────────────────────────────────────
	// Consulta
	// ─────────────────────────────────────────

	@Transactional(readOnly = true)
	public UsuarioResponse obtener(Long usuarioId) {
		return usuarioRepository.findById(usuarioId)
				.map(UsuarioResponse::desde)
				.orElseThrow(() -> new RecursoNoEncontradoException("El usuario no existe."));
	}

	private static ReglaNegocioException correoDuplicado() {
		return new ReglaNegocioException(CORREO_DUPLICADO, "Ya existe una cuenta con ese correo.");
	}
}
