package co.edu.unbosque.marketplace.reportes.aplicacion;

import co.edu.unbosque.marketplace.reportes.dominio.FormatoReporte;

/** El archivo exportado, listo para descargar (MA-85). */
public final class ArchivoReporte {

	private final String nombre;
	private final FormatoReporte formato;
	private final byte[] contenido;

	public ArchivoReporte(String nombre, FormatoReporte formato, byte[] contenido) {
		this.nombre = nombre;
		this.formato = formato;
		this.contenido = contenido.clone();
	}

	public String nombre() {
		return nombre;
	}

	public FormatoReporte formato() {
		return formato;
	}

	public byte[] contenido() {
		return contenido.clone();
	}
}
