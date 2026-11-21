package co.edu.unbosque.marketplace.busqueda.presentacion;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.busqueda.aplicacion.BusquedaService;
import co.edu.unbosque.marketplace.busqueda.aplicacion.CriteriosBusquedaDTO;
import co.edu.unbosque.marketplace.busqueda.aplicacion.ResultadoBusquedaDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Listado y búsqueda de alojamientos publicados (MA-61). Es pública: se puede buscar antes de
 * iniciar sesión. Sin parámetros lista todos los activos, de a 20 por página.
 */
@RestController
@Tag(name = "Búsqueda")
public class BusquedaController {

	private final BusquedaService busqueda;

	public BusquedaController(BusquedaService busqueda) {
		this.busqueda = busqueda;
	}

	@GetMapping("/api/alojamientos")
	@Operation(summary = "Listar los alojamientos publicados; se filtran por ciudad, fechas, huéspedes, tipo,"
			+ " precio y servicios")
	public ResultadoBusquedaDTO buscar(@Validated @ModelAttribute CriteriosBusquedaDTO criterios) {
		return busqueda.buscar(criterios);
	}
}
