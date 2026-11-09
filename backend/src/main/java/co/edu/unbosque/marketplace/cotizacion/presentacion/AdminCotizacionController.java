package co.edu.unbosque.marketplace.cotizacion.presentacion;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.cotizacion.aplicacion.ConfiguracionCotizacionDTO;
import co.edu.unbosque.marketplace.cotizacion.aplicacion.CotizacionService;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Tarifa de servicio y descuento por estadía larga (MA-65). /api/admin/** exige ADMINISTRADOR. */
@RestController
@RequestMapping("/api/admin/configuracion/cotizacion")
@Tag(name = "Administración de la cotización")
public class AdminCotizacionController {

	private final CotizacionService cotizacionService;

	public AdminCotizacionController(CotizacionService cotizacionService) {
		this.cotizacionService = cotizacionService;
	}

	@GetMapping
	@Operation(summary = "Ver la configuración de la cotización")
	public ConfiguracionCotizacionDTO obtener() {
		return cotizacionService.obtenerConfiguracion();
	}

	@PutMapping
	@Operation(summary = "Cambiar la tarifa de servicio y el descuento por estadía larga")
	public ConfiguracionCotizacionDTO actualizar(@AuthenticationPrincipal Usuario administrador,
			@Valid @RequestBody ConfiguracionCotizacionDTO datos) {
		return cotizacionService.actualizarConfiguracion(administrador, datos);
	}
}
