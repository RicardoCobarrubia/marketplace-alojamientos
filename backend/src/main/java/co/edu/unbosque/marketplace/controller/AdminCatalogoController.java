package co.edu.unbosque.marketplace.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.dto.CiudadDTO;
import co.edu.unbosque.marketplace.dto.ServicioDTO;
import co.edu.unbosque.marketplace.dto.TipoAlojamientoDTO;
import co.edu.unbosque.marketplace.service.CatalogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * El administrador agrega ciudades, tipos y servicios sin cambiar código ni redesplegar
 * (MA-53, escenario 3).
 */
@RestController
@RequestMapping("/api/admin/catalogos")
@Tag(name = "Administración de catálogos")
public class AdminCatalogoController {

	private final CatalogoService catalogoService;

	public AdminCatalogoController(CatalogoService catalogoService) {
		this.catalogoService = catalogoService;
	}

	@PostMapping("/ciudades")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Agregar una ciudad")
	public CiudadDTO crearCiudad(@Valid @RequestBody CiudadDTO solicitud) {
		return catalogoService.crearCiudad(solicitud);
	}

	@PostMapping("/tipos")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Agregar un tipo de alojamiento")
	public TipoAlojamientoDTO crearTipo(@Valid @RequestBody TipoAlojamientoDTO solicitud) {
		return catalogoService.crearTipo(solicitud);
	}

	@PostMapping("/servicios")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Agregar un servicio")
	public ServicioDTO crearServicio(@Valid @RequestBody ServicioDTO solicitud) {
		return catalogoService.crearServicio(solicitud);
	}
}
