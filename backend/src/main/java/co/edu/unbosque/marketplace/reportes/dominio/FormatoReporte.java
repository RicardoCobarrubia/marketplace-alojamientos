package co.edu.unbosque.marketplace.reportes.dominio;

/** Formatos en los que se exporta un reporte (MA-85). */
public enum FormatoReporte {

	PDF("application/pdf", "pdf"),
	EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

	private final String tipoContenido;
	private final String extension;

	FormatoReporte(String tipoContenido, String extension) {
		this.tipoContenido = tipoContenido;
		this.extension = extension;
	}

	public String tipoContenido() {
		return tipoContenido;
	}

	public String extension() {
		return extension;
	}
}
