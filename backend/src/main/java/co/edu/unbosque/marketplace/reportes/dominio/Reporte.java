package co.edu.unbosque.marketplace.reportes.dominio;

import java.util.List;

/**
 * El contenido de un reporte, sin formato: un título, unas líneas de encabezado y sus secciones,
 * cada una una tabla. El mismo contenido sale en PDF o en Excel (MA-85).
 *
 * @param titulo título del documento
 * @param encabezado líneas que van debajo del título (período, quién lo generó y cuándo)
 * @param secciones tablas del reporte, en orden
 */
public record Reporte(String titulo, List<String> encabezado, List<Seccion> secciones) {

	public Reporte {
		encabezado = List.copyOf(encabezado);
		secciones = List.copyOf(secciones);
	}

	/**
	 * Una tabla del reporte. Los valores de cada fila van en el orden de las columnas: los números
	 * quedan como números en Excel y el resto como texto.
	 */
	public record Seccion(String titulo, List<String> columnas, List<List<Object>> filas) {

		public Seccion {
			columnas = List.copyOf(columnas);
			filas = List.copyOf(filas);
		}
	}
}
