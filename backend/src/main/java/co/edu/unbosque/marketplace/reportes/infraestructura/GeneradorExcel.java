package co.edu.unbosque.marketplace.reportes.infraestructura;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import co.edu.unbosque.marketplace.reportes.dominio.FormatoReporte;
import co.edu.unbosque.marketplace.reportes.dominio.GeneradorReporte;
import co.edu.unbosque.marketplace.reportes.dominio.Reporte;
import co.edu.unbosque.marketplace.reportes.dominio.Reporte.Seccion;

/**
 * Reporte en Excel con Apache POI (MA-85): una hoja de resumen con el encabezado y una hoja por
 * sección. Usa el modo de escritura por partes de POI, que solo tiene en memoria unas cuantas filas:
 * con 100.000 reservas no se llena la memoria.
 */
@Component
public class GeneradorExcel implements GeneradorReporte {

	private static final int FILAS_EN_MEMORIA = 200;
	private static final int ANCHO_COLUMNA = 20 * 256;

	@Override
	public FormatoReporte formato() {
		return FormatoReporte.EXCEL;
	}

	@Override
	public byte[] generar(Reporte reporte) {
		SXSSFWorkbook libro = new SXSSFWorkbook(FILAS_EN_MEMORIA);
		try (libro; ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			CellStyle negrita = negrita(libro);
			Sheet portada = libro.createSheet("Reporte");
			portada.setColumnWidth(0, ANCHO_COLUMNA * 4);
			escribir(portada.createRow(0), List.of(reporte.titulo()), negrita);
			int fila = 1;
			for (String linea : reporte.encabezado()) {
				escribir(portada.createRow(fila++), List.of(linea), null);
			}
			for (Seccion seccion : reporte.secciones()) {
				hoja(libro, seccion, negrita);
			}
			libro.write(salida);
			return salida.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo generar el Excel", e);
		} finally {
			// Borra los archivos temporales en los que POI escribe las filas
			libro.dispose();
		}
	}

	private static void hoja(SXSSFWorkbook libro, Seccion seccion, CellStyle negrita) {
		Sheet hoja = libro.createSheet(WorkbookUtil.createSafeSheetName(seccion.titulo()));
		for (int columna = 0; columna < seccion.columnas().size(); columna++) {
			hoja.setColumnWidth(columna, ANCHO_COLUMNA);
		}
		escribir(hoja.createRow(0), seccion.columnas(), negrita);
		hoja.createFreezePane(0, 1);
		int fila = 1;
		for (List<Object> valores : seccion.filas()) {
			escribir(hoja.createRow(fila++), valores, null);
		}
	}

	private static void escribir(Row fila, List<?> valores, CellStyle estilo) {
		for (int columna = 0; columna < valores.size(); columna++) {
			Object valor = valores.get(columna);
			if (valor == null) {
				// Un dato vacío queda como celda vacía
				continue;
			}
			Cell celda = fila.createCell(columna);
			if (valor instanceof Number numero) {
				celda.setCellValue(numero.doubleValue());
			} else {
				celda.setCellValue(valor.toString());
			}
			if (estilo != null) {
				celda.setCellStyle(estilo);
			}
		}
	}

	private static CellStyle negrita(SXSSFWorkbook libro) {
		Font fuente = libro.createFont();
		fuente.setBold(true);
		CellStyle estilo = libro.createCellStyle();
		estilo.setFont(fuente);
		return estilo;
	}
}
