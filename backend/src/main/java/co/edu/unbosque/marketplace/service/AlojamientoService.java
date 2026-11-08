package co.edu.unbosque.marketplace.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.dto.AlojamientoDTO;
import co.edu.unbosque.marketplace.dto.ServicioAdicionalDTO;
import co.edu.unbosque.marketplace.exception.AccesoDenegadoException;
import co.edu.unbosque.marketplace.exception.InformacionIncompletaException;
import co.edu.unbosque.marketplace.exception.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.exception.ReglaNegocioException;
import co.edu.unbosque.marketplace.exception.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.model.Alojamiento;
import co.edu.unbosque.marketplace.model.Alojamiento.EstadoAlojamiento;
import co.edu.unbosque.marketplace.model.DatosAlojamiento;
import co.edu.unbosque.marketplace.model.Servicio;
import co.edu.unbosque.marketplace.model.ServicioAdicional;
import co.edu.unbosque.marketplace.model.Usuario;
import co.edu.unbosque.marketplace.model.Usuario.Rol;
import co.edu.unbosque.marketplace.repository.AlojamientoRepository;
import co.edu.unbosque.marketplace.repository.CiudadRepository;
import co.edu.unbosque.marketplace.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplace.repository.ServicioAdicionalRepository;
import co.edu.unbosque.marketplace.repository.ServicioRepository;
import co.edu.unbosque.marketplace.repository.TipoAlojamientoRepository;
import co.edu.unbosque.marketplace.repository.UsuarioRepository;

/**
 * Publicación y administración de alojamientos (MA-53 a MA-57).
 * <p>
 * El rol se valida en el controlador con {@code @PreAuthorize}; aquí se valida la propiedad del
 * recurso: solo el anfitrión dueño o un administrador pueden modificarlo, y un alojamiento
 * BLOQUEADA solo lo edita el administrador (regla de negocio 6, ADR-009).
 */
@Service
public class AlojamientoService {

	private static final Logger log = LoggerFactory.getLogger(AlojamientoService.class);

	private final AlojamientoRepository alojamientoRepository;
	private final ServicioAdicionalRepository servicioAdicionalRepository;
	private final UsuarioRepository usuarioRepository;
	private final CiudadRepository ciudadRepository;
	private final TipoAlojamientoRepository tipoRepository;
	private final ServicioRepository servicioRepository;
	private final PoliticaCancelacionRepository politicaRepository;
	private final Clock reloj;

	public AlojamientoService(AlojamientoRepository alojamientoRepository,
			ServicioAdicionalRepository servicioAdicionalRepository, UsuarioRepository usuarioRepository,
			CiudadRepository ciudadRepository, TipoAlojamientoRepository tipoRepository,
			ServicioRepository servicioRepository, PoliticaCancelacionRepository politicaRepository,
			Clock reloj) {
		this.alojamientoRepository = alojamientoRepository;
		this.servicioAdicionalRepository = servicioAdicionalRepository;
		this.usuarioRepository = usuarioRepository;
		this.ciudadRepository = ciudadRepository;
		this.tipoRepository = tipoRepository;
		this.servicioRepository = servicioRepository;
		this.politicaRepository = politicaRepository;
		this.reloj = reloj;
	}

	// ─────────────────────────────────────────
	// Registrar y editar — MA-53, MA-54
	// ─────────────────────────────────────────

	/** Crea el alojamiento en BORRADOR, asociado al anfitrión que lo registra. */
	@Transactional
	public AlojamientoDTO crear(Usuario anfitrion, AlojamientoDTO solicitud) {
		Usuario dueno = usuarioRepository.getReferenceById(anfitrion.getId());
		Alojamiento alojamiento = alojamientoRepository.save(
				new Alojamiento(dueno, resolverDatos(solicitud), reloj.instant()));
		// Solo se registran ids numéricos y enums: no pueden traer saltos de línea.
		log.info("Alojamiento registrado (alojamiento {}, anfitrión {})", alojamiento.getId(), anfitrion.getId()); // NOSONAR
		return AlojamientoDTO.desde(alojamiento);
	}

	/**
	 * Edita la información. Solo el dueño o un administrador; si está BLOQUEADA, solo el
	 * administrador (MA-54, escenario 2). Las reservas ya confirmadas guardan su propia copia
	 * del precio en la cotización, así que no cambian (escenario 3).
	 */
	@Transactional
	public AlojamientoDTO editar(Usuario actor, Long alojamientoId, AlojamientoDTO solicitud) {
		Alojamiento alojamiento = buscar(alojamientoId);
		verificarPuedeModificar(actor, alojamiento);
		if (alojamiento.getEstado() == EstadoAlojamiento.BLOQUEADA && !esAdministrador(actor)) {
			throw new AccesoDenegadoException("El alojamiento está bloqueado; solo un administrador puede editarlo.");
		}
		alojamiento.actualizarInformacion(resolverDatos(solicitud), reloj.instant());
		log.info("Alojamiento editado (alojamiento {}, usuario {})", alojamientoId, actor.getId()); // NOSONAR: solo ids numéricos
		return AlojamientoDTO.desde(alojamiento);
	}

	// ─────────────────────────────────────────
	// Consultas — MA-56, escenario 3
	// ─────────────────────────────────────────

	/** El dueño y el administrador ven cualquier estado; los demás solo los ACTIVA. */
	@Transactional(readOnly = true)
	public AlojamientoDTO obtener(Usuario actor, Long alojamientoId) {
		Alojamiento alojamiento = buscar(alojamientoId);
		boolean puedeVerlo = alojamiento.getEstado() == EstadoAlojamiento.ACTIVA
				|| esAdministrador(actor) || alojamiento.esDe(actor.getId());
		if (!puedeVerlo) {
			// Se responde como si no existiera para no revelar publicaciones no activas
			throw noEncontrado();
		}
		return AlojamientoDTO.desde(alojamiento);
	}

	/** El anfitrión consulta solo los suyos. */
	@Transactional(readOnly = true)
	public List<AlojamientoDTO> listarDelAnfitrion(Usuario anfitrion) {
		return alojamientoRepository.findByAnfitrionIdOrderByCreadoEnDesc(anfitrion.getId()).stream()
				.map(AlojamientoDTO::desde)
				.toList();
	}

	/** El administrador consulta todos, o los de un estado (por ejemplo PENDIENTE_REVISION). */
	@Transactional(readOnly = true)
	public List<AlojamientoDTO> listarTodos(EstadoAlojamiento estado) {
		List<Alojamiento> alojamientos = estado == null
				? alojamientoRepository.findAllByOrderByCreadoEnDesc()
				: alojamientoRepository.findByEstadoOrderByActualizadoEnAsc(estado);
		return alojamientos.stream().map(AlojamientoDTO::desde).toList();
	}

	// ─────────────────────────────────────────
	// Estados del anfitrión — MA-56
	// ─────────────────────────────────────────

	/** BORRADOR → PENDIENTE_REVISION, solo si tiene toda la información mínima. */
	@Transactional
	public AlojamientoDTO enviarARevision(Usuario anfitrion, Long alojamientoId) {
		Alojamiento alojamiento = buscarDelDueno(anfitrion, alojamientoId);
		List<String> faltantes = alojamiento.camposFaltantes();
		if (!faltantes.isEmpty()) {
			throw new InformacionIncompletaException(faltantes);
		}
		EstadoAlojamiento anterior = alojamiento.getEstado();
		alojamiento.enviarARevision(reloj.instant());
		registrarCambioDeEstado(alojamiento, anterior, anfitrion);
		return AlojamientoDTO.desde(alojamiento);
	}

	/** ACTIVA → PAUSADA. No cancela reservas ni requiere nueva revisión. */
	@Transactional
	public AlojamientoDTO pausar(Usuario anfitrion, Long alojamientoId) {
		return cambiarEstadoDelDueno(anfitrion, alojamientoId, EstadoAlojamiento.ACTIVA, EstadoAlojamiento.PAUSADA);
	}

	/** PAUSADA → ACTIVA, sin nueva revisión. */
	@Transactional
	public AlojamientoDTO reanudar(Usuario anfitrion, Long alojamientoId) {
		return cambiarEstadoDelDueno(anfitrion, alojamientoId, EstadoAlojamiento.PAUSADA, EstadoAlojamiento.ACTIVA);
	}

	// ─────────────────────────────────────────
	// Revisión del administrador — MA-57
	// ─────────────────────────────────────────

	/** PENDIENTE_REVISION → ACTIVA. */
	@Transactional
	public AlojamientoDTO aprobar(Usuario administrador, Long alojamientoId) {
		Alojamiento alojamiento = buscar(alojamientoId);
		verificarPendiente(alojamiento);
		EstadoAlojamiento anterior = alojamiento.getEstado();
		alojamiento.cambiarEstado(EstadoAlojamiento.ACTIVA, reloj.instant());
		registrarCambioDeEstado(alojamiento, anterior, administrador);
		return AlojamientoDTO.desde(alojamiento);
	}

	/** PENDIENTE_REVISION → BORRADOR, con el motivo para el anfitrión. */
	@Transactional
	public AlojamientoDTO rechazar(Usuario administrador, Long alojamientoId, String motivo) {
		Alojamiento alojamiento = buscar(alojamientoId);
		verificarPendiente(alojamiento);
		EstadoAlojamiento anterior = alojamiento.getEstado();
		alojamiento.rechazar(motivo, reloj.instant());
		registrarCambioDeEstado(alojamiento, anterior, administrador);
		return AlojamientoDTO.desde(alojamiento);
	}

	// ─────────────────────────────────────────
	// Servicios adicionales — MA-55
	// ─────────────────────────────────────────

	@Transactional
	public ServicioAdicionalDTO agregarServicioAdicional(Usuario anfitrion, Long alojamientoId,
			ServicioAdicionalDTO solicitud) {
		Alojamiento alojamiento = buscarDelDuenoSinBloqueo(anfitrion, alojamientoId);
		ServicioAdicional servicio = alojamiento.agregarServicioAdicional(solicitud.nombre(), solicitud.costo(),
				solicitud.modalidad(), reloj.instant());
		servicio = servicioAdicionalRepository.save(servicio);
		log.info("Servicio adicional agregado (alojamiento {}, servicio {})", alojamientoId, servicio.getId());
		return ServicioAdicionalDTO.desde(servicio);
	}

	@Transactional
	public ServicioAdicionalDTO editarServicioAdicional(Usuario anfitrion, Long alojamientoId, Long servicioId,
			ServicioAdicionalDTO solicitud) {
		ServicioAdicional servicio = buscarServicioAdicional(anfitrion, alojamientoId, servicioId);
		servicio.actualizar(solicitud.nombre(), solicitud.costo(), solicitud.modalidad());
		return ServicioAdicionalDTO.desde(servicio);
	}

	/** Lo retira de la oferta sin borrarlo, para no afectar reservas que ya lo contrataron. */
	@Transactional
	public void retirarServicioAdicional(Usuario anfitrion, Long alojamientoId, Long servicioId) {
		buscarServicioAdicional(anfitrion, alojamientoId, servicioId).retirar();
	}

	// ─────────────────────────────────────────
	// Auxiliares
	// ─────────────────────────────────────────

	private DatosAlojamiento resolverDatos(AlojamientoDTO solicitud) {
		Set<Servicio> servicios = new HashSet<>(servicioRepository.findAllById(solicitud.servicioIds()));
		if (servicios.size() != solicitud.servicioIds().size()) {
			throw new SolicitudInvalidaException("CATALOGO_INEXISTENTE", "Algún servicio elegido no existe.");
		}
		return new DatosAlojamiento(
				solicitud.titulo(),
				solicitud.descripcion(),
				solicitud.ubicacion(),
				tipoRepository.findById(solicitud.tipoId())
						.orElseThrow(() -> catalogoInexistente("El tipo de alojamiento no existe.")),
				ciudadRepository.findById(solicitud.ciudadId())
						.orElseThrow(() -> catalogoInexistente("La ciudad no existe.")),
				politicaRepository.findById(solicitud.politicaCancelacionId())
						.orElseThrow(() -> catalogoInexistente("La política de cancelación no existe.")),
				solicitud.capacidad(),
				solicitud.precioNoche(),
				solicitud.tarifaLimpieza(),
				servicios,
				solicitud.reglas(),
				solicitud.imagenes());
	}

	/**
	 * Pausar y reanudar solo parten de un estado concreto. Sin esto, el anfitrión podría activar
	 * un alojamiento PENDIENTE_REVISION sin la aprobación del administrador, o sacar uno BLOQUEADA.
	 */
	private AlojamientoDTO cambiarEstadoDelDueno(Usuario anfitrion, Long alojamientoId,
			EstadoAlojamiento origen, EstadoAlojamiento destino) {
		Alojamiento alojamiento = buscarDelDueno(anfitrion, alojamientoId);
		EstadoAlojamiento anterior = alojamiento.getEstado();
		if (anterior != origen) {
			throw new ReglaNegocioException("TRANSICION_INVALIDA",
					"No se puede pasar de " + anterior + " a " + destino + ".");
		}
		alojamiento.cambiarEstado(destino, reloj.instant());
		registrarCambioDeEstado(alojamiento, anterior, anfitrion);
		return AlojamientoDTO.desde(alojamiento);
	}

	private ServicioAdicional buscarServicioAdicional(Usuario anfitrion, Long alojamientoId, Long servicioId) {
		return buscarDelDuenoSinBloqueo(anfitrion, alojamientoId).buscarServicioAdicional(servicioId)
				.filter(ServicioAdicional::isActivo)
				.orElseThrow(() -> new RecursoNoEncontradoException("El servicio adicional no existe."));
	}

	private Alojamiento buscar(Long alojamientoId) {
		return alojamientoRepository.findById(alojamientoId).orElseThrow(AlojamientoService::noEncontrado);
	}

	/** Solo el anfitrión dueño (MA-55 escenario 3, MA-56). */
	private Alojamiento buscarDelDueno(Usuario anfitrion, Long alojamientoId) {
		Alojamiento alojamiento = buscar(alojamientoId);
		if (!alojamiento.esDe(anfitrion.getId())) {
			throw new AccesoDenegadoException("Solo el anfitrión dueño puede hacer esto.");
		}
		return alojamiento;
	}

	/** Para cambiar la oferta: un alojamiento BLOQUEADA no lo modifica el anfitrión (ADR-009). */
	private Alojamiento buscarDelDuenoSinBloqueo(Usuario anfitrion, Long alojamientoId) {
		Alojamiento alojamiento = buscarDelDueno(anfitrion, alojamientoId);
		if (alojamiento.getEstado() == EstadoAlojamiento.BLOQUEADA) {
			throw new AccesoDenegadoException("El alojamiento está bloqueado; no puedes modificarlo.");
		}
		return alojamiento;
	}

	private void verificarPuedeModificar(Usuario actor, Alojamiento alojamiento) {
		if (!esAdministrador(actor) && !alojamiento.esDe(actor.getId())) {
			throw new AccesoDenegadoException("Solo el anfitrión dueño o un administrador pueden modificarlo.");
		}
	}

	private static void verificarPendiente(Alojamiento alojamiento) {
		if (alojamiento.getEstado() != EstadoAlojamiento.PENDIENTE_REVISION) {
			throw new ReglaNegocioException("NO_PENDIENTE",
					"Solo se pueden revisar alojamientos en PENDIENTE_REVISION.");
		}
	}

	private static boolean esAdministrador(Usuario usuario) {
		return usuario.getRol() == Rol.ADMINISTRADOR;
	}

	/**
	 * Deja el cambio en el log estructurado con el id de correlación. El registro en la tabla de
	 * auditoría se agrega con MA-87.
	 */
	private void registrarCambioDeEstado(Alojamiento alojamiento, EstadoAlojamiento anterior, Usuario actor) {
		Instant ahora = reloj.instant();
		log.info("Cambio de estado de alojamiento {}: {} -> {} (usuario {}, rol {}, {})", // NOSONAR: solo ids, enums y fecha
				alojamiento.getId(), anterior, alojamiento.getEstado(), actor.getId(), actor.getRol(), ahora);
	}

	private static RecursoNoEncontradoException noEncontrado() {
		return new RecursoNoEncontradoException("El alojamiento no existe.");
	}

	private static SolicitudInvalidaException catalogoInexistente(String mensaje) {
		return new SolicitudInvalidaException("CATALOGO_INEXISTENTE", mensaje);
	}
}
