package co.edu.unbosque.marketplace.alojamientos.aplicacion;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.alojamientos.dominio.Ciudad;
import co.edu.unbosque.marketplace.alojamientos.dominio.Servicio;
import co.edu.unbosque.marketplace.alojamientos.dominio.TipoAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.CiudadRepository;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.ServicioRepository;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.TipoAlojamientoRepository;
import co.edu.unbosque.marketplace.comun.excepcion.ReglaNegocioException;

/**
 * Catálogos de ciudades, tipos de alojamiento, servicios y políticas de cancelación.
 * El administrador agrega ciudades, tipos y servicios desde la API, sin cambiar código ni
 * redesplegar (MA-53, escenario 3).
 */
@Service
public class CatalogoService {

	private static final Logger log = LoggerFactory.getLogger(CatalogoService.class);
	private static final String YA_EXISTE = "CATALOGO_DUPLICADO";

	private final CiudadRepository ciudadRepository;
	private final TipoAlojamientoRepository tipoRepository;
	private final ServicioRepository servicioRepository;
	private final PoliticaCancelacionRepository politicaRepository;
	private final Auditoria auditoria;

	public CatalogoService(CiudadRepository ciudadRepository, TipoAlojamientoRepository tipoRepository,
			ServicioRepository servicioRepository, PoliticaCancelacionRepository politicaRepository,
			Auditoria auditoria) {
		this.ciudadRepository = ciudadRepository;
		this.tipoRepository = tipoRepository;
		this.servicioRepository = servicioRepository;
		this.politicaRepository = politicaRepository;
		this.auditoria = auditoria;
	}

	// ─────────────────────────────────────────
	// Consultas (públicas: las usa también la búsqueda)
	// ─────────────────────────────────────────

	@Transactional(readOnly = true)
	public List<CiudadDTO> listarCiudades() {
		return ciudadRepository.findAllByOrderByNombreAsc().stream().map(CiudadDTO::desde).toList();
	}

	@Transactional(readOnly = true)
	public List<TipoAlojamientoDTO> listarTipos() {
		return tipoRepository.findAllByOrderByNombreAsc().stream().map(TipoAlojamientoDTO::desde).toList();
	}

	@Transactional(readOnly = true)
	public List<ServicioDTO> listarServicios() {
		return servicioRepository.findAllByOrderByNombreAsc().stream().map(ServicioDTO::desde).toList();
	}

	@Transactional(readOnly = true)
	public List<PoliticaCancelacionDTO> listarPoliticasCancelacion() {
		return politicaRepository.findAllByOrderByNombreAsc().stream().map(PoliticaCancelacionDTO::desde).toList();
	}

	// ─────────────────────────────────────────
	// Altas del administrador — MA-53, escenario 3
	// ─────────────────────────────────────────

	@Transactional
	public CiudadDTO crearCiudad(CiudadDTO solicitud) {
		if (ciudadRepository.existsByNombreIgnoreCaseAndDepartamentoIgnoreCase(
				solicitud.nombre().trim(), solicitud.departamento().trim())) {
			throw new ReglaNegocioException(YA_EXISTE, "Esa ciudad ya existe.");
		}
		Ciudad ciudad = ciudadRepository.save(new Ciudad(solicitud.nombre(), solicitud.departamento()));
		log.info("Ciudad agregada al catálogo (ciudad {})", ciudad.getId());
		auditarAlta("CREAR_CIUDAD", "CIUDAD", ciudad.getId(), ciudad.getNombre());
		return CiudadDTO.desde(ciudad);
	}

	@Transactional
	public TipoAlojamientoDTO crearTipo(TipoAlojamientoDTO solicitud) {
		if (tipoRepository.existsByCodigo(solicitud.codigo())) {
			throw new ReglaNegocioException(YA_EXISTE, "Ya existe un tipo con ese código.");
		}
		TipoAlojamiento tipo = tipoRepository.save(new TipoAlojamiento(solicitud.codigo(), solicitud.nombre()));
		log.info("Tipo de alojamiento agregado al catálogo ({})", tipo.getCodigo());
		auditarAlta("CREAR_TIPO_ALOJAMIENTO", "TIPO_ALOJAMIENTO", tipo.getId().longValue(), tipo.getCodigo());
		return TipoAlojamientoDTO.desde(tipo);
	}

	@Transactional
	public ServicioDTO crearServicio(ServicioDTO solicitud) {
		if (servicioRepository.existsByCodigo(solicitud.codigo())) {
			throw new ReglaNegocioException(YA_EXISTE, "Ya existe un servicio con ese código.");
		}
		Servicio servicio = servicioRepository.save(new Servicio(solicitud.codigo(), solicitud.nombre()));
		log.info("Servicio agregado al catálogo ({})", servicio.getCodigo());
		auditarAlta("CREAR_SERVICIO", "SERVICIO", servicio.getId().longValue(), servicio.getCodigo());
		return ServicioDTO.desde(servicio);
	}

	/** Acción administrativa auditada en la misma transacción que el alta (MA-87). */
	private void auditarAlta(String accion, String entidad, Long id, String detalle) {
		auditoria.registrar(EventoAuditoria.accion(accion, entidad, id, EventoAuditoria.Resultado.EXITO, detalle));
	}
}
