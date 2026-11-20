package co.edu.unbosque.marketplace.moderacion.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import co.edu.unbosque.marketplace.alojamientos.aplicacion.AlojamientoService;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento.EstadoAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.AlojamientoRepository;
import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.comun.configuracion.CacheConfig;
import co.edu.unbosque.marketplace.comun.excepcion.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.comun.excepcion.ReglaNegocioException;
import co.edu.unbosque.marketplace.notificaciones.aplicacion.PublicadorEventos;
import co.edu.unbosque.marketplace.reservas.aplicacion.RevisionReservasService;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.EstadoCuenta;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;
import co.edu.unbosque.marketplace.usuarios.infraestructura.UsuarioRepository;

/**
 * Acciones de moderación del administrador (MA-80, MA-81). Cada una ocurre en una sola
 * transacción con su registro de auditoría y los cambios en cascada (alojamientos pausados,
 * reservas en revisión): no queda una moderación a medias. Las reservas nunca se cancelan
 * automáticamente (MA-82, escenario 3).
 */
@Service
public class ModeracionService {

	private static final Logger log = LoggerFactory.getLogger(ModeracionService.class);
	private static final String ALOJAMIENTO = "ALOJAMIENTO";
	private static final String USUARIO = "USUARIO";

	private final AlojamientoRepository alojamientoRepository;
	private final UsuarioRepository usuarioRepository;
	private final RevisionReservasService revisionReservas;
	private final Auditoria auditoria;
	private final CacheManager cacheManager;
	private final PublicadorEventos publicador;
	private final Clock reloj;

	public ModeracionService(AlojamientoRepository alojamientoRepository, UsuarioRepository usuarioRepository,
			RevisionReservasService revisionReservas, Auditoria auditoria, CacheManager cacheManager,
			PublicadorEventos publicador, Clock reloj) {
		this.alojamientoRepository = alojamientoRepository;
		this.usuarioRepository = usuarioRepository;
		this.revisionReservas = revisionReservas;
		this.auditoria = auditoria;
		this.cacheManager = cacheManager;
		this.publicador = publicador;
		this.reloj = reloj;
	}

	// ─────────────────────────────────────────
	// Alojamientos — MA-80
	// ─────────────────────────────────────────

	/**
	 * Bloquea el alojamiento con motivo: sale de las búsquedas, no acepta reservas nuevas y sus
	 * reservas confirmadas futuras pasan a EN_REVISION sin cancelarse.
	 */
	@Transactional
	public ResultadoModeracionDTO bloquearAlojamiento(Long alojamientoId, String motivo) {
		Alojamiento alojamiento = alojamiento(alojamientoId);
		EstadoAlojamiento anterior = alojamiento.getEstado();
		alojamiento.bloquear(motivo, reloj.instant());
		auditoria.registrar(EventoAuditoria.cambio("BLOQUEAR_ALOJAMIENTO", ALOJAMIENTO, alojamientoId, anterior,
				EstadoAlojamiento.BLOQUEADA, alojamiento.getMotivoBloqueo()));
		publicador.publicar("ALOJAMIENTO_BLOQUEADO", ALOJAMIENTO, alojamientoId,
				AlojamientoService.datosDelEvento(alojamiento, alojamiento.getMotivoBloqueo()));
		int enRevision = revisionReservas.ponerEnRevisionDelAlojamiento(alojamientoId,
				"Alojamiento bloqueado: " + alojamiento.getMotivoBloqueo());
		log.info("Alojamiento {} bloqueado; {} reservas en revisión", alojamiento.getId(), enRevision);
		return new ResultadoModeracionDTO(ALOJAMIENTO, alojamientoId, alojamiento.getEstado().name(),
				alojamiento.getMotivoBloqueo(), enRevision, 0);
	}

	/** Lo desbloquea: queda PAUSADA y el anfitrión decide cuándo reanudarlo. */
	@Transactional
	public ResultadoModeracionDTO desbloquearAlojamiento(Long alojamientoId) {
		Alojamiento alojamiento = alojamiento(alojamientoId);
		alojamiento.desbloquear(reloj.instant());
		auditoria.registrar(EventoAuditoria.cambio("DESBLOQUEAR_ALOJAMIENTO", ALOJAMIENTO, alojamientoId,
				EstadoAlojamiento.BLOQUEADA, EstadoAlojamiento.PAUSADA, null));
		return new ResultadoModeracionDTO(ALOJAMIENTO, alojamientoId, alojamiento.getEstado().name(), null, 0, 0);
	}

	// ─────────────────────────────────────────
	// Usuarios — MA-81
	// ─────────────────────────────────────────

	/**
	 * Restringe al usuario con motivo: no puede iniciar sesión, sus tokens dejan de valer en esta
	 * réplica de inmediato y en las demás en máximo 60 s (la caché de sesiones, ADR-010), sus
	 * alojamientos activos pasan a PAUSADA y sus reservas confirmadas futuras a EN_REVISION.
	 */
	@Transactional
	public ResultadoModeracionDTO restringirUsuario(Long usuarioId, String motivo) {
		Usuario usuario = usuario(usuarioId);
		if (usuario.getRol() == Rol.ADMINISTRADOR) {
			throw new ReglaNegocioException("NO_SE_PUEDE_RESTRINGIR", "No se puede restringir a un administrador.");
		}
		EstadoCuenta anterior = usuario.getEstadoCuenta();
		usuario.restringir(motivo);
		auditoria.registrar(EventoAuditoria.cambio("RESTRINGIR_USUARIO", USUARIO, usuarioId, anterior,
				EstadoCuenta.RESTRINGIDA, usuario.getMotivoRestriccion()));
		int pausados = pausarAlojamientos(usuarioId);
		int enRevision = revisionReservas.ponerEnRevisionDelUsuario(usuarioId,
				"Usuario restringido: " + usuario.getMotivoRestriccion());
		olvidarSesionAlConfirmar(usuario.getEmail());
		log.info("Usuario {} restringido; {} alojamientos pausados y {} reservas en revisión", usuario.getId(),
				pausados, enRevision);
		return new ResultadoModeracionDTO(USUARIO, usuarioId, usuario.getEstadoCuenta().name(),
				usuario.getMotivoRestriccion(), enRevision, pausados);
	}

	/**
	 * Levanta la restricción: la cuenta vuelve a ACTIVA, o sigue DESACTIVADA si su dueño la había
	 * desactivado. Sus alojamientos siguen PAUSADA.
	 */
	@Transactional
	public ResultadoModeracionDTO levantarRestriccion(Long usuarioId) {
		Usuario usuario = usuario(usuarioId);
		usuario.levantarRestriccion();
		auditoria.registrar(EventoAuditoria.cambio("LEVANTAR_RESTRICCION", USUARIO, usuarioId,
				EstadoCuenta.RESTRINGIDA, usuario.getEstadoCuenta(), null));
		olvidarSesionAlConfirmar(usuario.getEmail());
		return new ResultadoModeracionDTO(USUARIO, usuarioId, usuario.getEstadoCuenta().name(), null, 0, 0);
	}

	private int pausarAlojamientos(Long anfitrionId) {
		Instant ahora = reloj.instant();
		List<Alojamiento> activos = alojamientoRepository.findByAnfitrionIdAndEstado(anfitrionId,
				EstadoAlojamiento.ACTIVA);
		for (Alojamiento alojamiento : activos) {
			alojamiento.cambiarEstado(EstadoAlojamiento.PAUSADA, ahora);
			auditoria.registrar(EventoAuditoria.cambio("PAUSAR_ALOJAMIENTO", ALOJAMIENTO, alojamiento.getId(),
					EstadoAlojamiento.ACTIVA, EstadoAlojamiento.PAUSADA, "Restricción del anfitrión"));
		}
		return activos.size();
	}

	/**
	 * Saca al usuario de la caché de sesiones de esta réplica cuando la transacción se confirma:
	 * su siguiente petición vuelve a leer la cuenta y el token viejo ya no vale.
	 */
	private void olvidarSesionAlConfirmar(String email) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				Cache cache = cacheManager.getCache(CacheConfig.VERSIONES_SESION);
				if (cache != null) {
					cache.evict(email);
				}
			}
		});
	}

	private Alojamiento alojamiento(Long alojamientoId) {
		return alojamientoRepository.buscarParaActualizar(alojamientoId)
				.orElseThrow(() -> new RecursoNoEncontradoException("El alojamiento no existe."));
	}

	private Usuario usuario(Long usuarioId) {
		return usuarioRepository.buscarParaActualizar(usuarioId)
				.orElseThrow(() -> new RecursoNoEncontradoException("El usuario no existe."));
	}
}
