package co.edu.unbosque.marketplace.perfiles.aplicacion;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria.Resultado;
import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.notificaciones.dominio.EnvioCorreo;
import co.edu.unbosque.marketplace.perfiles.aplicacion.EmisionCodigos.CodigoEmitido;
import co.edu.unbosque.marketplace.perfiles.dominio.CodigoRecuperacion;
import co.edu.unbosque.marketplace.perfiles.infraestructura.CodigoRecuperacionRepository;
import co.edu.unbosque.marketplace.usuarios.aplicacion.SesionesEnCache;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.infraestructura.UsuarioRepository;
import jakarta.annotation.PreDestroy;

/**
 * Recuperar la contraseña con un código de un solo uso enviado al correo (MA-50).
 * <p>
 * Nunca revela si un correo está registrado: la solicitud responde lo mismo exista o no, y crear
 * el código y enviarlo ocurre en otro hilo, así la respuesta tampoco tarda distinto. Al cambiar la
 * contraseña, un correo inexistente o sin código vigente hace la misma comprobación costosa que
 * uno con código.
 */
@Service
public class RecuperacionContrasenaService {

	private static final Logger log = LoggerFactory.getLogger(RecuperacionContrasenaService.class);
	private static final String USUARIO = "USUARIO";
	private static final String CAMBIAR_CONTRASENA = "CAMBIAR_CONTRASENA";
	private static final int MAXIMO_BYTES_CONTRASENA = 72;

	private final EmisionCodigos emision;
	private final UsuarioRepository usuarios;
	private final CodigoRecuperacionRepository codigos;
	private final PasswordEncoder passwordEncoder;
	private final EnvioCorreo correo;
	private final SesionesEnCache sesiones;
	private final Auditoria auditoria;
	private final Clock reloj;
	/** Para comparar contra algo cuando no hay código: el tiempo no delata si la cuenta existe. */
	private final String hashFicticio;
	private final ExecutorService envios = Executors.newVirtualThreadPerTaskExecutor();

	public RecuperacionContrasenaService(EmisionCodigos emision, UsuarioRepository usuarios,
			CodigoRecuperacionRepository codigos, PasswordEncoder passwordEncoder, EnvioCorreo correo,
			SesionesEnCache sesiones, Auditoria auditoria, Clock reloj) {
		this.emision = emision;
		this.usuarios = usuarios;
		this.codigos = codigos;
		this.passwordEncoder = passwordEncoder;
		this.correo = correo;
		this.sesiones = sesiones;
		this.auditoria = auditoria;
		this.reloj = reloj;
		this.hashFicticio = passwordEncoder.encode("000000");
	}

	@PreDestroy
	void detener() {
		envios.close();
	}

	/**
	 * MA-50, escenario 1: responde de inmediato y en otro hilo crea un código de 6 dígitos que vale
	 * 15 minutos y lo envía al correo. Los logs de ese hilo conservan el id de correlación.
	 */
	public void solicitar(String email) {
		Map<String, String> contexto = MDC.getCopyOfContextMap();
		envios.execute(() -> {
			if (contexto != null) {
				MDC.setContextMap(contexto);
			}
			try {
				emision.emitir(email).ifPresent(this::enviar);
			} catch (RuntimeException e) {
				log.warn("No se pudo crear o enviar el código de recuperación", e);
			} finally {
				MDC.clear();
			}
		});
	}

	/**
	 * MA-50, escenarios 2 y 3: con el último código, sin usar ni vencer, cambia la contraseña, la
	 * anterior deja de servir y se cierran las sesiones abiertas. Un código equivocado gasta un
	 * intento aunque la solicitud se rechace.
	 */
	@Transactional(noRollbackFor = SolicitudInvalidaException.class)
	public void cambiarContrasena(NuevaContrasenaDTO solicitud) {
		if (solicitud.password().getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES_CONTRASENA) {
			throw new SolicitudInvalidaException("CONTRASENA_MUY_LARGA",
					"La contraseña es demasiado larga; usa menos caracteres especiales.");
		}
		Instant ahora = reloj.instant();
		Optional<Usuario> registrado = usuarios.findByEmail(Usuario.normalizarEmail(solicitud.email()))
				.flatMap(encontrado -> usuarios.buscarParaActualizar(encontrado.getId()));
		Optional<CodigoRecuperacion> vigente = registrado
				.flatMap(usuario -> codigos.findFirstByUsuarioIdOrderByIdDesc(usuario.getId()))
				.filter(ultimo -> ultimo.sirve(ahora));
		boolean coincide = passwordEncoder.matches(solicitud.codigo(),
				vigente.map(CodigoRecuperacion::getCodigoHash).orElse(hashFicticio));
		if (registrado.isEmpty() || vigente.isEmpty()) {
			throw codigoInvalido();
		}
		Usuario usuario = registrado.get();
		CodigoRecuperacion codigo = vigente.get();
		if (!coincide) {
			codigo.registrarIntentoFallido();
			auditoria.registrar(EventoAuditoria.accion(CAMBIAR_CONTRASENA, USUARIO, usuario.getId(),
					Resultado.RECHAZADO, "Código incorrecto").conActor(usuario.getId(), usuario.getRol()));
			throw codigoInvalido();
		}
		codigo.usar(ahora);
		usuario.cambiarContrasena(passwordEncoder.encode(solicitud.password()));
		auditoria.registrar(EventoAuditoria.accion(CAMBIAR_CONTRASENA, USUARIO, usuario.getId(),
				Resultado.EXITO, "Con código de recuperación").conActor(usuario.getId(), usuario.getRol()));
		sesiones.olvidarAlConfirmar(usuario.getEmail());
		log.info("Contraseña cambiada con código de recuperación (usuario {})", usuario.getId());
	}

	/** El mismo error para un correo que no existe y para un código usado, vencido o equivocado. */
	private static SolicitudInvalidaException codigoInvalido() {
		return new SolicitudInvalidaException("CODIGO_INVALIDO",
				"El código no es válido o ya venció. Solicita uno nuevo.");
	}

	private void enviar(CodigoEmitido emitido) {
		correo.enviar(emitido.destinatario(), "Código para recuperar tu contraseña",
				"Tu código para recuperar la contraseña en LR Alojamientos es " + emitido.codigo() + ".\n\n"
						+ "Vence en 15 minutos y sirve una sola vez. Si no lo pediste, ignora este correo:"
						+ " tu contraseña no cambia.");
	}
}
