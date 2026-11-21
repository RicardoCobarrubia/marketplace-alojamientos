package co.edu.unbosque.marketplace.reportes.aplicacion;

import java.util.Arrays;
import java.util.Objects;

import co.edu.unbosque.marketplace.reportes.dominio.FormatoReporte;

/**
 * El archivo exportado, listo para descargar (MA-85). El contenido se copia al entrar y al salir:
 * nadie puede cambiar el archivo después de generado.
 */
public record ArchivoReporte(String nombre, FormatoReporte formato, byte[] contenido) {

	public ArchivoReporte {
		contenido = contenido.clone();
	}

	@Override
	public byte[] contenido() {
		return contenido.clone();
	}

	@Override
	public boolean equals(Object otro) {
		return otro instanceof ArchivoReporte archivo && nombre.equals(archivo.nombre)
				&& formato == archivo.formato && Arrays.equals(contenido, archivo.contenido);
	}

	@Override
	public int hashCode() {
		return Objects.hash(nombre, formato, Arrays.hashCode(contenido));
	}

	@Override
	public String toString() {
		return "ArchivoReporte[nombre=" + nombre + ", formato=" + formato + ", bytes=" + contenido.length + "]";
	}
}
