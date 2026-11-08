package co.edu.unbosque.marketplace.dto;

import co.edu.unbosque.marketplace.model.PoliticaCancelacion;

/** DTO de política de cancelación. Solo se consulta; las políticas las define el equipo. */
public record PoliticaCancelacionDTO(Short id, String codigo, String nombre) {

	public static PoliticaCancelacionDTO desde(PoliticaCancelacion politica) {
		return new PoliticaCancelacionDTO(politica.getId(), politica.getCodigo(), politica.getNombre());
	}
}
