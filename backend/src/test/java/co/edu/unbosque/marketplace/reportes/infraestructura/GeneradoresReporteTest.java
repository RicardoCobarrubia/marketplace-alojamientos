package co.edu.unbosque.marketplace.reportes.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import co.edu.unbosque.marketplace.reportes.dominio.FormatoReporte;
import co.edu.unbosque.marketplace.reportes.dominio.Reporte;
import co.edu.unbosque.marketplace.reportes.dominio.Reporte.Seccion;

class GeneradoresReporteTest {

	private static Reporte reporte(int filasDeReservas) {
		List<List<Object>> reservas = new ArrayList<>();
		for (int i = 1; i <= filasDeReservas; i++) {
			reservas.add(Arrays.asList((long) i, "Medellín", new BigDecimal("15000.50"), null));
		}
		return new Reporte("Reporte de gestión", List.of("Período: del 2026-10-01 al 2026-10-30"), List.of(
				new Seccion("Resumen", List.of("Indicador", "Valor"),
						List.of(Arrays.asList("Usuarios", 12L))),
				new Seccion("Reservas", List.of("Id", "Ciudad", "Tarifa", "Nota"), reservas),
				new Seccion("Vacía", List.of("Columna"), List.of())));
	}

	@Test
	void elExcelTieneUnaHojaPorSeccionConNumerosComoNumeros() throws IOException {
		GeneradorExcel generador = new GeneradorExcel();
		assertThat(generador.formato()).isEqualTo(FormatoReporte.EXCEL);

		byte[] archivo = generador.generar(reporte(3));

		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
			assertThat(libro.getNumberOfSheets()).isEqualTo(4);
			assertThat(libro.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("Reporte de gestión");
			assertThat(libro.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).contains("2026-10-01");
			assertThat(libro.getSheet("Resumen").getRow(1).getCell(1).getNumericCellValue()).isEqualTo(12);
			Sheet reservas = libro.getSheet("Reservas");
			assertThat(reservas.getLastRowNum()).isEqualTo(3);
			assertThat(reservas.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Tarifa");
			assertThat(reservas.getRow(3).getCell(1).getStringCellValue()).isEqualTo("Medellín");
			assertThat(reservas.getRow(3).getCell(2).getNumericCellValue()).isEqualTo(15000.5);
			assertThat(reservas.getRow(3).getCell(3)).isNull();
			assertThat(libro.getSheet("Vacía").getLastRowNum()).isZero();
		}
	}

	@Test
	void elPdfMuestraElTituloYLasTablasAunqueSeanLargas() throws IOException {
		GeneradorPdf generador = new GeneradorPdf();
		assertThat(generador.formato()).isEqualTo(FormatoReporte.PDF);

		byte[] archivo = generador.generar(reporte(1200));

		assertThat(new String(archivo, 0, 5)).isEqualTo("%PDF-");
		PdfReader lector = new PdfReader(archivo);
		try {
			assertThat(lector.getNumberOfPages()).isGreaterThan(1);
			String primera = new PdfTextExtractor(lector).getTextFromPage(1);
			assertThat(primera).contains("Reporte de gestión").contains("Resumen").contains("Usuarios")
					.contains("15000.50");
			String ultima = new PdfTextExtractor(lector).getTextFromPage(lector.getNumberOfPages());
			assertThat(ultima).contains("1200").contains("Sin datos en el período.");
		} finally {
			lector.close();
		}
	}
}
