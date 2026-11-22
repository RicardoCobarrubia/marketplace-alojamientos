package co.edu.unbosque.marketplace.usuarios.aplicacion;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.EstadoCuenta;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;
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

import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria.Resultado;
import co.edu.unbosque.marketplace.comun.excepcion.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.comun.excepcion.ReglaNegocioException;
import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.usuarios.dominio.CredencialesInvalidasException;
import co.edu.unbosque.marketplace.usuarios.dominio.CuentaBloqueadaTemporalmenteException;
import co.edu.unbosque.marketplace.usuarios.dominio.CuentaDesactivadaException;
import co.edu.unbosque.marketplace.usuarios.dominio.CuentaRestringidaException;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.infraestructura.UsuarioRepository;

/**
 * Registro, inicio de sesión, perfil y reactivación de usuarios (MA-47, MA-48, MA-51, MA-52).
 * <p>
 * El login usa el {@code AuthenticationManager} de Spring Security, como en Software 1, y responde
 * con excepciones en lugar de códigos numéricos. El token lo genera el controlador con JwtUtil.
 */
@Service
public class UsuarioService {

	private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);
	static final String CORREO_DUPLICADO = "CORREO_YA_REGISTRADO";
	private static final int MAXIMO_BYTES_CONTRASENA = 72;
	private static final String USUARIO = "USUARIO";
	private static final String INICIO_SESION = "INICIO_SESION";

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final Auditoria auditoria;
	private final SesionesEnCache sesiones;
	private final Clock reloj;
	private final int intentosMaximos;
	private final Duration duracionBloqueo;

	public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, Auditoria auditoria, SesionesEnCache sesiones, Clock reloj,
			@Value("${marketplace.seguridad.intentos-maximos}") int intentosMaximos,
			@Value("${marketplace.seguridad.duracion-bloqueo}") Duration duracionBloqueo) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.auditoria = auditoria;
		this.sesiones = sesiones;
		this.reloj = reloj;
		this.intentosMaximos = intentosMaximos;
		this.duracionBloqueo = duracionBloqueo;
	}

	// ---- Lo que el módulo de usuarios ofrece a los demás módulos (ADR-001) ----
	// Sin @Transactional propio: se unen a la transacción de quien llama, con sus bloqueos.

	public Optional<Usuario> buscarPorId(Long id) {
		return usuarioRepository.findById(id);
	}

	/** Busca por correo; el correo se normaliza aquí. */
	public Optional<Usuario> buscarPorEmail(String email) {
		return usuarioRepository.findByEmail(Usuario.normalizarEmail(email));
	}

	/** Bloquea la fila de la cuenta hasta terminar la transacción de quien llama. */
	public Optional<Usuario> buscarParaActualizar(Long id) {
		return usuarioRepository.buscarParaActualizar(id);
	}

	/** Una referencia a la cuenta sin leerla, para asociarla a otra entidad. */
	public Usuario referencia(Long id) {
		return usuarioRepository.getReferenceById(id);
	}

	public List<Usuario> administradoresActivos() {
		return usuarioRepository.findByRolAndEstadoCuenta(Rol.ADMINISTRADOR, EstadoCuenta.ACTIVA);
	}

	/** El estado de las cuentas con bloqueo compartido (MA-81): espera una restricción en curso. */
	public List<String> estadosConBloqueoCompartido(List<Long> ids) {
		return usuarioRepository.estadosConBloqueoCompartido(ids);
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
		auditoria.registrar(EventoAuditoria.cambio("REGISTRO_USUARIO", USUARIO, usuario.getId(), null,
				usuario.getEstadoCuenta(), null).conActor(usuario.getId(), usuario.getRol()));
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
	 * El intento fallido se guarda aunque se lance la excepción, por eso {@code noRollbackFor}; con
	 * él queda su registro de auditoría, sin la contraseña (MA-87).
	 */
	@Transactional(noRollbackFor = { CredencialesInvalidasException.class,
			CuentaBloqueadaTemporalmenteException.class, CuentaRestringidaException.class,
			CuentaDesactivadaException.class })
	public Usuario iniciarSesion(UsuarioDTO solicitud) {
		return autenticar(solicitud, false);
	}

	/**
	 * Reactiva una cuenta desactivada por su dueño (MA-52, escenario 3): el usuario inicia sesión,
	 * el sistema le responde que la cuenta está desactivada y él confirma con las mismas
	 * credenciales. Tiene las mismas protecciones del inicio de sesión: la contraseña se revisa
	 * primero, los intentos fallidos cuentan y una cuenta restringida sigue sin entrar. Sus
	 * alojamientos siguen en PAUSADA: el anfitrión decide cuándo reanudarlos. Si la cuenta ya
	 * estaba activa, es un inicio de sesión normal.
	 */
	@Transactional(noRollbackFor = { CredencialesInvalidasException.class,
			CuentaBloqueadaTemporalmenteException.class, CuentaRestringidaException.class })
	public Usuario reactivar(UsuarioDTO solicitud) {
		return autenticar(solicitud, true);
	}

	private Usuario autenticar(UsuarioDTO solicitud, boolean reactivar) {
		Instant ahora = reloj.instant();
		String email = Usuario.normalizarEmail(solicitud.email());
		Optional<Usuario> registrado = usuarioRepository.buscarParaLogin(email);

		if (registrado.isPresent() && registrado.get().estaBloqueadoTemporalmente(ahora)) {
			auditarInicioRechazado(registrado, "Cuenta bloqueada temporalmente");
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
			auditarInicioRechazado(registrado, "Credenciales inválidas");
			throw new CredencialesInvalidasException();
		} catch (LockedException e) {
			auditarInicioRechazado(registrado, "Cuenta restringida");
			throw new CuentaRestringidaException();
		} catch (DisabledException e) {
			// La contraseña ya se comprobó: Spring revisa el estado de la cuenta después
			if (!reactivar || registrado.isEmpty()) {
				auditarInicioRechazado(registrado, "Cuenta desactivada");
				throw new CuentaDesactivadaException();
			}
			autenticacion = UsernamePasswordAuthenticationToken.authenticated(registrado.get(), null,
					registrado.get().getAuthorities());
			reactivarCuenta(registrado.get());
		}

		Usuario usuario = (Usuario) autenticacion.getPrincipal();
		usuario.registrarInicioSesionExitoso();
		usuarioRepository.save(usuario);
		log.info("Inicio de sesión exitoso (usuario {}, rol {})", usuario.getId(), usuario.getRol());
		auditoria.registrar(EventoAuditoria.accion(INICIO_SESION, USUARIO, usuario.getId(), Resultado.EXITO, null)
				.conActor(usuario.getId(), usuario.getRol()));
		return usuario;
	}

	private void reactivarCuenta(Usuario usuario) {
		usuario.reactivar();
		log.info("Cuenta reactivada (usuario {})", usuario.getId());
		auditoria.registrar(EventoAuditoria.cambio("REACTIVAR_CUENTA", USUARIO, usuario.getId(),
				Usuario.EstadoCuenta.DESACTIVADA, Usuario.EstadoCuenta.ACTIVA, null)
				.conActor(usuario.getId(), usuario.getRol()));
		// Esta réplica pudo guardar la cuenta desactivada: sin olvidarla, el token nuevo no valdría
		sesiones.olvidarAlConfirmar(usuario.getEmail());
	}

	/**
	 * Un intento rechazado. Si el correo no existe no se guarda cuál se intentó: solo que hubo un
	 * intento, sin datos que permitan adivinar cuentas.
	 */
	private void auditarInicioRechazado(Optional<Usuario> registrado, String motivo) {
		EventoAuditoria evento = EventoAuditoria.accion(INICIO_SESION, USUARIO,
				registrado.map(Usuario::getId).orElse(null), Resultado.RECHAZADO, motivo);
		auditoria.registrar(registrado.map(usuario -> evento.conActor(usuario.getId(), usuario.getRol()))
				.orElse(evento));
	}

	// ─────────────────────────────────────────
	// Perfil — MA-51
	// ─────────────────────────────────────────

	@Transactional(readOnly = true)
	public UsuarioDTO obtener(Long usuarioId) {
		return usuarioRepository.findById(usuarioId)
				.map(UsuarioDTO::desde)
				.orElseThrow(() -> new RecursoNoEncontradoException("El usuario no existe."));
	}

	/**
	 * Cambia el nombre y el teléfono del usuario de la sesión (escenario 2). El correo y el rol no
	 * se pueden cambiar: si llegan distintos a los actuales se rechaza, en lugar de ignorarlos en
	 * silencio. La contraseña tampoco se cambia aquí.
	 */
	@Transactional
	public UsuarioDTO actualizarPerfil(Long usuarioId, UsuarioDTO cambios) {
		Usuario usuario = usuarioRepository.buscarParaActualizar(usuarioId)
				.orElseThrow(() -> new RecursoNoEncontradoException("El usuario no existe."));
		boolean cambiaCorreo = cambios.email() != null
				&& !Usuario.normalizarEmail(cambios.email()).equals(usuario.getEmail());
		boolean cambiaRol = cambios.rol() != null && cambios.rol() != usuario.getRol();
		if (cambiaCorreo || cambiaRol || cambios.password() != null) {
			throw new SolicitudInvalidaException("CAMPO_NO_EDITABLE",
					"Desde el perfil solo se pueden cambiar el nombre y el teléfono.");
		}
		usuario.actualizarPerfil(cambios.nombre(), cambios.telefono());
		// Sin los datos personales: solo que el perfil cambió
		auditoria.registrar(EventoAuditoria.accion("ACTUALIZAR_PERFIL", USUARIO, usuarioId, Resultado.EXITO, null)
				.conActor(usuarioId, usuario.getRol()));
		sesiones.olvidarAlConfirmar(usuario.getEmail());
		return UsuarioDTO.desde(usuario);
	}

	private static ReglaNegocioException correoDuplicado() {
		return new ReglaNegocioException(CORREO_DUPLICADO, "Ya existe una cuenta con ese correo.");
	}
}
