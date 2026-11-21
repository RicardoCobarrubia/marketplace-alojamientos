package co.edu.unbosque.marketplace.panel.presentacion;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.panel.aplicacion.IndicadoresDTO;
import co.edu.unbosque.marketplace.panel.aplicacion.IngresosDTO;
import co.edu.unbosque.marketplace.panel.aplicacion.PanelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Panel del administrador (MA-83, MA-84). Las rutas {@code /api/admin/**} exigen el rol
 * ADMINISTRADOR: cualquier otro rol recibe 403 (MA-83, escenario 2).
 */
@RestController
@RequestMapping("/api/admin/panel")
@Tag(name = "Panel")
public class AdminPanelController {

	private final PanelService panel;

	public AdminPanelController(PanelService panel) {
		this.panel = panel;
	}

	@GetMapping("/indicadores")
	@Operation(summary = "Usuarios, alojamientos activos y reservas por estado")
	public IndicadoresDTO indicadores() {
		return panel.indicadores();
	}

	@GetMapping("/ingresos")
	@Operation(summary = "Ingresos estimados y las 5 ciudades con más reservas; por defecto, los últimos 30 días")
	public IngresosDTO ingresos(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate hasta) {
		return panel.ingresos(desde, hasta);
	}
}
