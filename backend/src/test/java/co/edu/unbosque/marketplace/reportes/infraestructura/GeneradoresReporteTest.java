package co.edu.unbosque.marketplace.reportes.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.LongStream;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import co.edu.unbosque.marketplace.reportes.dominio.FormatoReporte;
import co.edu.unbosque.marketplace.reportes.dominio.Reporte;
import co.edu.unbosque.marketplace.reportes.dominio.Reporte.Seccion;

class GeneradoresReporteTest {

	private static final Pattern FILA_PDF = Pattern.compile("(?m)^\\s*(\\d+) Medellín");

	private static Reporte reporte(int filasDeReservas) {
		List<List<Object>> reservas = new ArrayList<>();
		for (int i = 1; i <= filasDeReservas; i++) {
			reservas.add(Arrays.asList((long) i, "Medellín", new BigDecimal("15000.50"), null));
		}
		return new Reporte("Reporte de gestión", List.of("Período: del 2026-10-01 al 2026-10-30"), List.of(
				new Seccion("Resumen", List.of("Indicador", "Valor"),
						List.of(Arrays.asList("Usuarios", 12L), Arrays.asList("Łukasz 李", 1L))),
				new Seccion("Reservas", List.of("Id", "Ciudad", "Tarifa", "Nota"), reservas),
				new Seccion("Vacía", List.of("Columna"), List.of())));
	}

	private static List<Long> secuencia(int hasta) {
		return LongStream.rangeClosed(1, hasta).boxed().toList();
	}

	@Test
	void elExcelTieneUnaHojaPorSeccionYConservaTodasLasFilas() throws IOException {
		GeneradorExcel generador = new GeneradorExcel();
		assertThat(generador.formato()).isEqualTo(FormatoReporte.EXCEL);

		// Más filas que las que POI tiene en memoria a la vez (200)
		byte[] archivo = generador.generar(reporte(450));

		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
			assertThat(libro.getNumberOfSheets()).isEqualTo(4);
			assertThat(libro.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("Reporte de gestión");
			assertThat(libro.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).contains("2026-10-01");
			Sheet resumen = libro.getSheet("Resumen");
			assertThat(resumen.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(12);
			assertThat(resumen.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Łukasz 李");
			Sheet reservas = libro.getSheet("Reservas");
			assertThat(reservas.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Tarifa");
			List<Long> ids = new ArrayList<>();
			for (int fila = 1; fila <= reservas.getLastRowNum(); fila++) {
				ids.add((long) reservas.getRow(fila).getCell(0).getNumericCellValue());
			}
			assertThat(ids).isEqualTo(secuencia(450));
			assertThat(reservas.getRow(450).getCell(1).getStringCellValue()).isEqualTo("Medellín");
			assertThat(reservas.getRow(450).getCell(2).getNumericCellValue()).isEqualTo(15000.5);
			assertThat(reservas.getRow(450).getCell(3)).isNull();
			assertThat(libro.getSheet("Vacía").getLastRowNum()).isZero();
		}
	}

	@Test
	void elPdfConservaTodasLasFilasEntreLasPartesDeLasTablasLargas() throws IOException {
		GeneradorPdf generador = new GeneradorPdf();
		assertThat(generador.formato()).isEqualTo(FormatoReporte.PDF);

		// Tres partes de 500 filas: se revisan los límites entre ellas
		byte[] archivo = generador.generar(reporte(1200));

		assertThat(new String(archivo, 0, 5)).isEqualTo("%PDF-");
		try (PdfReader lector = new PdfReader(archivo)) {
			PdfTextExtractor extractor = new PdfTextExtractor(lector);
			StringBuilder texto = new StringBuilder();
			for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
				texto.append(extractor.getTextFromPage(pagina)).append('\n');
			}
			assertThat(texto).contains("Reporte de gestión").contains("Resumen").contains("15000.50")
					.contains("Sin datos en el período.");
			List<Long> ids = new ArrayList<>();
			Matcher fila = FILA_PDF.matcher(texto);
			while (fila.find()) {
				ids.add(Long.parseLong(fila.group(1)));
			}
			assertThat(ids).isEqualTo(secuencia(1200));
		}
	}

	@Test
	void enElPdfLosCaracteresQueHelveticaNoDibujaSalenComoSignoDePregunta() throws IOException {
		assertThat(GeneradorPdf.imprimible("Peña Güiza")).isEqualTo("Peña Güiza");
		assertThat(GeneradorPdf.imprimible("Łukasz 李雷")).isEqualTo("?ukasz ??");

		try (PdfReader lector = new PdfReader(new GeneradorPdf().generar(reporte(1)))) {
			assertThat(new PdfTextExtractor(lector).getTextFromPage(1)).contains("?ukasz ?");
		}
	}
}
