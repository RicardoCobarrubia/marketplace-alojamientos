package co.edu.unbosque.marketplace.auditoria.presentacion;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.auditoria.aplicacion.ConsultaAuditoriaService;
import co.edu.unbosque.marketplace.auditoria.aplicacion.RegistroAuditoriaDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Registro de auditoría (MA-87). Las rutas {@code /api/admin/**} exigen el rol ADMINISTRADOR. */
@RestController
@RequestMapping("/api/admin/auditoria")
@Tag(name = "Auditoría")
public class AdminAuditoriaController {

	private final ConsultaAuditoriaService consulta;

	public AdminAuditoriaController(ConsultaAuditoriaService consulta) {
		this.consulta = consulta;
	}

	@GetMapping
	@Operation(summary = "Los 200 registros más recientes; se puede filtrar por entidad (tipo e id), actor o acción")
	public List<RegistroAuditoriaDTO> consultar(@RequestParam(required = false) String entidad,
			@RequestParam(required = false) Long entidadId, @RequestParam(required = false) Long actorId,
			@RequestParam(required = false) String accion) {
		return consulta.consultar(entidad, entidadId, actorId, accion);
	}
}
