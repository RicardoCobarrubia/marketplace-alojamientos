package co.edu.unbosque.marketplace.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.dto.CiudadDTO;
import co.edu.unbosque.marketplace.dto.PoliticaCancelacionDTO;
import co.edu.unbosque.marketplace.dto.ServicioDTO;
import co.edu.unbosque.marketplace.dto.TipoAlojamientoDTO;
import co.edu.unbosque.marketplace.exception.ReglaNegocioException;
import co.edu.unbosque.marketplace.model.Ciudad;
import co.edu.unbosque.marketplace.model.Servicio;
import co.edu.unbosque.marketplace.model.TipoAlojamiento;
import co.edu.unbosque.marketplace.repository.CiudadRepository;
import co.edu.unbosque.marketplace.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplace.repository.ServicioRepository;
import co.edu.unbosque.marketplace.repository.TipoAlojamientoRepository;

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

	public CatalogoService(CiudadRepository ciudadRepository, TipoAlojamientoRepository tipoRepository,
			ServicioRepository servicioRepository, PoliticaCancelacionRepository politicaRepository) {
		this.ciudadRepository = ciudadRepository;
		this.tipoRepository = tipoRepository;
		this.servicioRepository = servicioRepository;
		this.politicaRepository = politicaRepository;
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
		return CiudadDTO.desde(ciudad);
	}

	@Transactional
	public TipoAlojamientoDTO crearTipo(TipoAlojamientoDTO solicitud) {
		if (tipoRepository.existsByCodigo(solicitud.codigo())) {
			throw new ReglaNegocioException(YA_EXISTE, "Ya existe un tipo con ese código.");
		}
		TipoAlojamiento tipo = tipoRepository.save(new TipoAlojamiento(solicitud.codigo(), solicitud.nombre()));
		log.info("Tipo de alojamiento agregado al catálogo ({})", tipo.getCodigo());
		return TipoAlojamientoDTO.desde(tipo);
	}

	@Transactional
	public ServicioDTO crearServicio(ServicioDTO solicitud) {
		if (servicioRepository.existsByCodigo(solicitud.codigo())) {
			throw new ReglaNegocioException(YA_EXISTE, "Ya existe un servicio con ese código.");
		}
		Servicio servicio = servicioRepository.save(new Servicio(solicitud.codigo(), solicitud.nombre()));
		log.info("Servicio agregado al catálogo ({})", servicio.getCodigo());
		return ServicioDTO.desde(servicio);
	}
}
