package co.edu.unbosque.marketplace.notificaciones.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.notificaciones.dominio.EventoOutbox;
import co.edu.unbosque.marketplace.notificaciones.dominio.Notificacion;
import co.edu.unbosque.marketplace.notificaciones.infraestructura.EventoOutboxRepository;
import co.edu.unbosque.marketplace.notificaciones.infraestructura.NotificacionRepository;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.EstadoCuenta;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;
import co.edu.unbosque.marketplace.usuarios.infraestructura.UsuarioRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Entrega un evento del outbox: genera sus notificaciones y lo marca ENTREGADO en la misma
 * transacción (ADR-007). Si falla, no queda nada a medias y el procesador lo reintenta.
 */
@Service
public class EntregaEventos {

	private static final Logger log = LoggerFactory.getLogger(EntregaEventos.class);
	/** Después de estos intentos el evento queda FALLIDO y deja de frenar a los demás. */
	static final int INTENTOS_MAXIMOS = 5;
	/** Largo de la columna {@code ultimo_error}. */
	private static final int LARGO_ERROR = 500;

	private final EventoOutboxRepository eventos;
	private final NotificacionRepository notificaciones;
	private final UsuarioRepository usuarios;
	private final ReglasNotificacion reglas;
	private final ObjectMapper json;
	private final Clock reloj;
	private final boolean conCorreo;

	public EntregaEventos(EventoOutboxRepository eventos, NotificacionRepository notificaciones,
			UsuarioRepository usuarios, ReglasNotificacion reglas, ObjectMapper json, Clock reloj,
			@Value("${marketplace.notificaciones.correo.activo}") boolean conCorreo) {
		this.eventos = eventos;
		this.notificaciones = notificaciones;
		this.usuarios = usuarios;
		this.reglas = reglas;
		this.json = json;
		this.reloj = reloj;
		this.conCorreo = conCorreo;
	}

	/**
	 * @return si lo entregó; si otra réplica lo tiene tomado o ya no está pendiente, no hace nada
	 */
	@Transactional
	public boolean entregar(Long eventoId) {
		EventoOutbox evento = eventos.tomarPendiente(eventoId).orElse(null);
		if (evento == null) {
			return false;
		}
		Instant ahora = reloj.instant();
		JsonNode datos = json.readTree(evento.getPayload());
		List<Aviso> avisos = reglas.avisos(evento.getTipo(), datos, this::administradores);
		for (Aviso aviso : avisos) {
			if (!notificaciones.existsByEventoIdAndUsuarioId(eventoId, aviso.usuarioId())) {
				notificaciones.save(new Notificacion(aviso.usuarioId(), eventoId, evento.getTipo(), aviso.titulo(),
						aviso.mensaje(), conCorreo, ahora));
			}
		}
		evento.entregar(ahora);
		log.debug("Evento {} ({}) entregado a {} personas", eventoId, evento.getTipo(), avisos.size());
		return true;
	}

	/**
	 * Suma el intento fallido en una transacción propia: la de la entrega ya se deshizo. Si otra
	 * réplica lo tiene tomado, espera: si esa lo entrega no lo cambia, y si falla también lo
	 * cuenta.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void registrarFallo(Long eventoId, String error) {
		String texto = error != null && error.length() > LARGO_ERROR ? error.substring(0, LARGO_ERROR) : error;
		eventos.registrarFallo(eventoId, texto, INTENTOS_MAXIMOS);
	}

	private List<Long> administradores() {
		return usuarios.findByRolAndEstadoCuenta(Rol.ADMINISTRADOR, EstadoCuenta.ACTIVA).stream()
				.map(Usuario::getId).toList();
	}
}
