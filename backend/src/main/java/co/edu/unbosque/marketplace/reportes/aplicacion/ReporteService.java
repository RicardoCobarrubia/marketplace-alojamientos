package co.edu.unbosque.marketplace.reportes.aplicacion;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria.Resultado;
import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.panel.aplicacion.PanelService;
import co.edu.unbosque.marketplace.panel.aplicacion.Periodo;
import co.edu.unbosque.marketplace.reportes.dominio.FormatoReporte;
import co.edu.unbosque.marketplace.reportes.dominio.GeneradorReporte;
import co.edu.unbosque.marketplace.reportes.dominio.Reporte;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;

/**
 * Exporta el reporte de gestión (MA-85) y deja constancia de quién lo pidió y cuándo (MA-86).
 * <p>
 * No es transaccional a propósito: la solicitud se guarda en la auditoría y se confirma antes de
 * generar el archivo. Si la generación falla o se interrumpe, el registro de la solicitud ya
 * existe (MA-86, escenario 2) y, si falla, queda además el registro del error.
 */
@Service
public class ReporteService {

	private static final Logger log = LoggerFactory.getLogger(ReporteService.class);
	static final String ACCION = "EXPORTAR_REPORTE";
	private static final String ENTIDAD = "REPORTE";
	private static final String SOLICITADO = "SOLICITADO";

	/** Un año como máximo: el archivo se arma en memoria antes de enviarlo. */
	static final long DIAS_MAXIMOS = 366;

	private final PanelService panel;
	private final ContenidoReporte contenido;
	private final Map<FormatoReporte, GeneradorReporte> generadores = new EnumMap<>(FormatoReporte.class);
	private final Auditoria auditoria;

	public ReporteService(PanelService panel, ContenidoReporte contenido, List<GeneradorReporte> generadores,
			Auditoria auditoria) {
		this.panel = panel;
		this.contenido = contenido;
		generadores.forEach(generador -> this.generadores.put(generador.formato(), generador));
		this.auditoria = auditoria;
	}

	public ArchivoReporte exportar(Usuario administrador, FormatoReporte formato, LocalDate desde, LocalDate hasta) {
		Periodo periodo = panel.periodo(desde, hasta);
		if (periodo.dias() > DIAS_MAXIMOS) {
			throw new SolicitudInvalidaException("RANGO_DEMASIADO_LARGO",
					"El reporte puede cubrir máximo " + DIAS_MAXIMOS + " días.");
		}
		String solicitud = formato + " del " + periodo.desde() + " al " + periodo.hasta();
		// MA-86: se registra y se confirma antes de generar nada
		registrar(administrador, null, SOLICITADO, Resultado.EXITO, solicitud);
		try {
			Reporte reporte = contenido.armar(periodo, administrador.getEmail());
			byte[] archivo = generadores.get(formato).generar(reporte);
			registrar(administrador, SOLICITADO, "GENERADO", Resultado.EXITO, solicitud + ", " + archivo.length
					+ " bytes");
			log.info("Reporte exportado (usuario {}, {}, {} bytes)", administrador.getId(), formato, archivo.length);
			String nombre = "reporte-gestion-" + periodo.desde() + "-a-" + periodo.hasta() + "." + formato.extension();
			return new ArchivoReporte(nombre, formato, archivo);
		} catch (RuntimeException e) {
			// Solo el tipo de error: el mensaje podría traer datos
			registrar(administrador, SOLICITADO, "FALLIDO", Resultado.ERROR,
					solicitud + " (" + e.getClass().getSimpleName() + ")");
			throw e;
		}
	}

	private void registrar(Usuario administrador, String anterior, String nuevo, Resultado resultado,
			String detalle) {
		auditoria.registrar(new EventoAuditoria(ACCION, ENTIDAD, null, anterior, nuevo, resultado, detalle,
				administrador.getId(), administrador.getRol().name()));
	}
}
