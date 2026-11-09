package co.edu.unbosque.marketplace.alojamientos.presentacion;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.alojamientos.aplicacion.CatalogoService;
import co.edu.unbosque.marketplace.alojamientos.aplicacion.CiudadDTO;
import co.edu.unbosque.marketplace.alojamientos.aplicacion.PoliticaCancelacionDTO;
import co.edu.unbosque.marketplace.alojamientos.aplicacion.ServicioDTO;
import co.edu.unbosque.marketplace.alojamientos.aplicacion.TipoAlojamientoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Catálogos para formularios y filtros. Son públicos: la búsqueda los necesita sin sesión. */
@RestController
@RequestMapping("/api/catalogos")
@Tag(name = "Catálogos")
@SecurityRequirements
public class CatalogoController {

	private final CatalogoService catalogoService;

	public CatalogoController(CatalogoService catalogoService) {
		this.catalogoService = catalogoService;
	}

	@GetMapping("/ciudades")
	@Operation(summary = "Listar ciudades")
	public List<CiudadDTO> listarCiudades() {
		return catalogoService.listarCiudades();
	}

	@GetMapping("/tipos")
	@Operation(summary = "Listar tipos de alojamiento")
	public List<TipoAlojamientoDTO> listarTipos() {
		return catalogoService.listarTipos();
	}

	@GetMapping("/servicios")
	@Operation(summary = "Listar servicios")
	public List<ServicioDTO> listarServicios() {
		return catalogoService.listarServicios();
	}

	@GetMapping("/politicas-cancelacion")
	@Operation(summary = "Listar políticas de cancelación")
	public List<PoliticaCancelacionDTO> listarPoliticasCancelacion() {
		return catalogoService.listarPoliticasCancelacion();
	}
}
