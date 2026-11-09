package co.edu.unbosque.marketplace.comun.excepcion;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Al alojamiento le falta información para enviarse a revisión. La respuesta incluye los
 * campos que faltan en {@code campos} (MA-56, escenario 1). HTTP 422.
 */
public class InformacionIncompletaException extends ExcepcionMarketplace {

	private static final long serialVersionUID = 1L;

	private final List<String> campos;

	public InformacionIncompletaException(List<String> campos) {
		super(HttpStatus.UNPROCESSABLE_CONTENT, "INFORMACION_INCOMPLETA", "Información incompleta",
				"Completa los campos indicados antes de enviar a revisión.");
		this.campos = List.copyOf(campos);
	}

	public List<String> getCampos() {
		return campos;
	}

	@Override
	public Map<String, Object> getPropiedadesAdicionales() {
		return Map.of("campos", campos);
	}
}
