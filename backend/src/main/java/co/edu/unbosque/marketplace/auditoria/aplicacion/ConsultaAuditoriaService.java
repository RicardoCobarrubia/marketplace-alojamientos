package co.edu.unbosque.marketplace.auditoria.aplicacion;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.auditoria.dominio.RegistroAuditoria;
import co.edu.unbosque.marketplace.auditoria.infraestructura.RegistroAuditoriaRepository;
import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;

/** Consulta del registro de auditoría para el administrador (MA-87). Solo lectura. */
@Service
public class ConsultaAuditoriaService {

	private final RegistroAuditoriaRepository registros;

	public ConsultaAuditoriaService(RegistroAuditoriaRepository registros) {
		this.registros = registros;
	}

	/**
	 * Los 200 registros más recientes, filtrados por una entidad (tipo e id), por un actor o por
	 * una acción; sin filtro, los últimos del sistema.
	 */
	@Transactional(readOnly = true)
	public List<RegistroAuditoriaDTO> consultar(String entidad, Long entidadId, Long actorId, String accion) {
		List<RegistroAuditoria> encontrados;
		if (entidad != null || entidadId != null) {
			if (entidad == null || entidadId == null) {
				throw new SolicitudInvalidaException("FILTRO_INCOMPLETO", "Para filtrar por entidad indica su tipo y su id.");
			}
			encontrados = registros.findTop200ByEntidadAndEntidadIdOrderByIdDesc(entidad, entidadId);
		} else if (actorId != null) {
			encontrados = registros.findTop200ByActorIdOrderByIdDesc(actorId);
		} else if (accion != null) {
			encontrados = registros.findTop200ByAccionOrderByIdDesc(accion);
		} else {
			encontrados = registros.findTop200ByOrderByIdDesc();
		}
		return encontrados.stream().map(RegistroAuditoriaDTO::desde).toList();
	}
}
