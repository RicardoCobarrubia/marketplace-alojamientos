package co.edu.unbosque.marketplace.service;

import java.nio.charset.StandardCharsets;
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

import co.edu.unbosque.marketplace.dto.UsuarioDTO;
import co.edu.unbosque.marketplace.exception.CredencialesInvalidasException;
import co.edu.unbosque.marketplace.exception.CuentaBloqueadaTemporalmenteException;
import co.edu.unbosque.marketplace.exception.CuentaDesactivadaException;
import co.edu.unbosque.marketplace.exception.CuentaRestringidaException;
import co.edu.unbosque.marketplace.exception.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.exception.ReglaNegocioException;
import co.edu.unbosque.marketplace.exception.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.model.Usuario;
import co.edu.unbosque.marketplace.repository.UsuarioRepository;

/**
 * Registro, inicio de sesión y consulta de usuarios (MA-47, MA-48).
 * <p>
 * El login usa el {@code AuthenticationManager} de Spring Security, como en Software 1, y responde
 * con excepciones en lugar de códigos numéricos. El token lo genera el controlador con JwtUtil.
 */
@Service
public class UsuarioService {

	private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);
	static final String CORREO_DUPLICADO = "CORREO_YA_REGISTRADO";
	private static final int MAXIMO_BYTES_CONTRASENA = 72;

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final Clock reloj;
	private final int intentosMaximos;
	private final Duration duracionBloqueo;

	public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, Clock reloj,
			@Value("${marketplace.seguridad.intentos-maximos}") int intentosMaximos,
			@Value("${marketplace.seguridad.duracion-bloqueo}") Duration duracionBloqueo) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
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
	public UsuarioDTO registrar(UsuarioDTO solicitud) {
		if (!solicitud.rol().sePuedeElegirAlRegistrarse()) {
			throw new SolicitudInvalidaException("ROL_NO_PERMITIDO",
					"Solo puedes registrarte como huésped o anfitrión.");
		}
		// BCrypt rechaza más de 72 bytes; con tildes o ñ un carácter ocupa más de un byte
		if (solicitud.password().getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES_CONTRASENA) {
			throw new SolicitudInvalidaException("CONTRASENA_MUY_LARGA",
					"La contraseña es demasiado larga; usa menos caracteres especiales.");
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
		return UsuarioDTO.desde(usuario);
	}

	// ─────────────────────────────────────────
	// Inicio de sesión — MA-48
	// ─────────────────────────────────────────

	/**
	 * Valida las credenciales con el {@code AuthenticationManager} y devuelve el usuario autenticado.
	 * <ul>
	 * <li>Correo o contraseña incorrectos: el mismo error en los dos casos (escenario 1).</li>
	 * <li>5 intentos fallidos seguidos: bloqueo temporal de 15 minutos.</li>
	 * <li>Cuenta restringida: no entra; desactivada: solo puede reactivarse (escenario 2).</li>
	 * </ul>
	 * El intento fallido se guarda aunque se lance la excepción, por eso {@code noRollbackFor}.
	 */
	@Transactional(noRollbackFor = CredencialesInvalidasException.class)
	public Usuario iniciarSesion(UsuarioDTO solicitud) {
		Instant ahora = reloj.instant();
		String email = Usuario.normalizarEmail(solicitud.email());
		Optional<Usuario> registrado = usuarioRepository.buscarParaLogin(email);

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
		log.info("Inicio de sesión exitoso (usuario {}, rol {})", usuario.getId(), usuario.getRol());
		return usuario;
	}

	// ─────────────────────────────────────────
	// Consulta
	// ─────────────────────────────────────────

	@Transactional(readOnly = true)
	public UsuarioDTO obtener(Long usuarioId) {
		return usuarioRepository.findById(usuarioId)
				.map(UsuarioDTO::desde)
				.orElseThrow(() -> new RecursoNoEncontradoException("El usuario no existe."));
	}

	private static ReglaNegocioException correoDuplicado() {
		return new ReglaNegocioException(CORREO_DUPLICADO, "Ya existe una cuenta con ese correo.");
	}
}
