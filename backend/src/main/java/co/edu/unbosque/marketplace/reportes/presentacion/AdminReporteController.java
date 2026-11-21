package co.edu.unbosque.marketplace.reportes.presentacion;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.reportes.aplicacion.ArchivoReporte;
import co.edu.unbosque.marketplace.reportes.aplicacion.ReporteService;
import co.edu.unbosque.marketplace.reportes.dominio.FormatoReporte;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Reportes de gestión (MA-85, MA-86). Las rutas {@code /api/admin/**} exigen el rol
 * ADMINISTRADOR: cualquier otro rol recibe 403 y el intento queda en la auditoría (MA-85,
 * escenario 3).
 */
@RestController
@RequestMapping("/api/admin/reportes")
@Tag(name = "Reportes")
public class AdminReporteController {

	private final ReporteService reportes;

	public AdminReporteController(ReporteService reportes) {
		this.reportes = reportes;
	}

	@GetMapping
	@Operation(summary = "Exportar el reporte de gestión en PDF o EXCEL; por defecto, los últimos 30 días")
	public ResponseEntity<byte[]> exportar(@AuthenticationPrincipal Usuario administrador,
			@RequestParam FormatoReporte formato,
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate hasta) {
		ArchivoReporte archivo = reportes.exportar(administrador, formato, desde, hasta);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(archivo.formato().tipoContenido()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
				.body(archivo.contenido());
	}
}
