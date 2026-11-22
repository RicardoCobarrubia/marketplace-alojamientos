package co.edu.unbosque.marketplace.notificaciones.aplicacion;

import java.time.Clock;

import co.edu.unbosque.marketplace.usuarios.aplicacion.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.notificaciones.dominio.EnvioCorreo;
import co.edu.unbosque.marketplace.notificaciones.dominio.Notificacion;
import co.edu.unbosque.marketplace.notificaciones.infraestructura.NotificacionRepository;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;

/**
 * Envía por correo la copia de una notificación (MA-75). Va aparte de la entrega del evento: un
 * servidor de correo lento o caído no la frena, y cada correo se reintenta por su cuenta.
 */
@Service
public class EnvioCorreos {

	private static final Logger log = LoggerFactory.getLogger(EnvioCorreos.class);
	/** Después de estos intentos el correo queda FALLIDO; la notificación interna sigue ahí. */
	static final int INTENTOS_MAXIMOS = 5;

	private final NotificacionRepository notificaciones;
	private final UsuarioService usuarioService;
	private final EnvioCorreo correo;
	private final Clock reloj;

	public EnvioCorreos(NotificacionRepository notificaciones, UsuarioService usuarioService, EnvioCorreo correo,
			Clock reloj) {
		this.notificaciones = notificaciones;
		this.usuarioService = usuarioService;
		this.correo = correo;
		this.reloj = reloj;
	}

	/**
	 * @return si lo envió; si otra réplica la tiene tomada o ya no está pendiente, no hace nada
	 * @throws co.edu.unbosque.marketplace.notificaciones.dominio.CorreoNoEnviadoException si el
	 *         servidor no lo aceptó; sigue pendiente
	 */
	@Transactional
	public boolean enviar(Long notificacionId) {
		Notificacion notificacion = notificaciones.tomarConCorreoPendiente(notificacionId).orElse(null);
		if (notificacion == null) {
			return false;
		}
		Usuario usuario = usuarioService.buscarPorId(notificacion.getUsuarioId())
				.orElseThrow(() -> new IllegalStateException("El usuario de la notificación ya no existe."));
		correo.enviar(usuario.getEmail(), "Inversiones LR: " + notificacion.getTitulo(), cuerpo(usuario, notificacion));
		notificacion.correoEnviado(reloj.instant());
		log.debug("Correo de la notificación {} enviado", notificacionId);
		return true;
	}

	/**
	 * Suma el intento fallido en una transacción propia: la del envío ya se deshizo. Si otra
	 * réplica tiene la notificación, espera: si esa envió el correo no la cambia.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void registrarFallo(Long notificacionId) {
		notificaciones.registrarFalloDeCorreo(notificacionId, INTENTOS_MAXIMOS);
	}

	private static String cuerpo(Usuario usuario, Notificacion notificacion) {
		return """
				Hola, %s:

				%s

				Puedes ver todas tus notificaciones en la plataforma.

				Inversiones LR""".formatted(usuario.getNombre(), notificacion.getMensaje());
	}
}
