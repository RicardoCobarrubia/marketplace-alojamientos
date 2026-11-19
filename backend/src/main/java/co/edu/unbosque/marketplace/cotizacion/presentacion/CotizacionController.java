package co.edu.unbosque.marketplace.cotizacion.presentacion;

import java.time.LocalDate;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.cotizacion.aplicacion.CotizacionDTO;
import co.edu.unbosque.marketplace.cotizacion.aplicacion.CotizacionService;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Cotización de una estadía (MA-63, MA-64). */
@RestController
@Tag(name = "Cotización")
public class CotizacionController {

	private final CotizacionService cotizacionService;

	public CotizacionController(CotizacionService cotizacionService) {
		this.cotizacionService = cotizacionService;
	}

	// GET /api/alojamientos/{id}/cotizacion?llegada=2026-12-20&salida=2026-12-23&servicios=4,5
	@GetMapping("/api/alojamientos/{alojamientoId}/cotizacion")
	@Operation(summary = "Cotizar una estadía con su desglose (pública para alojamientos ACTIVA)")
	public CotizacionDTO cotizar(@AuthenticationPrincipal Usuario usuario, @PathVariable Long alojamientoId,
			@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate llegada,
			@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate salida,
			@RequestParam(required = false) Set<Long> servicios) {
		return cotizacionService.cotizar(usuario, alojamientoId, llegada, salida, servicios);
	}
}
