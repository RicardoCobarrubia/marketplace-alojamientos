package co.edu.unbosque.marketplace.reportes.infraestructura;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import co.edu.unbosque.marketplace.reportes.dominio.FormatoReporte;
import co.edu.unbosque.marketplace.reportes.dominio.GeneradorReporte;
import co.edu.unbosque.marketplace.reportes.dominio.Reporte;
import co.edu.unbosque.marketplace.reportes.dominio.Reporte.Seccion;

/**
 * Reporte en PDF con OpenPDF (MA-85): página horizontal, una tabla por sección con el encabezado
 * repetido en cada página. Las tablas largas se escriben por partes para no tenerlas completas
 * en memoria.
 */
@Component
public class GeneradorPdf implements GeneradorReporte {

	private static final int FILAS_POR_PARTE = 500;
	private static final Font TITULO = new Font(Font.HELVETICA, 16, Font.BOLD);
	private static final Font SUBTITULO = new Font(Font.HELVETICA, 12, Font.BOLD);
	private static final Font NORMAL = new Font(Font.HELVETICA, 9);
	private static final Font ENCABEZADO = new Font(Font.HELVETICA, 9, Font.BOLD);
	private static final Color GRIS = new Color(230, 230, 230);

	@Override
	public FormatoReporte formato() {
		return FormatoReporte.PDF;
	}

	@Override
	public byte[] generar(Reporte reporte) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		Document documento = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);
		try {
			PdfWriter.getInstance(documento, salida);
			documento.open();
			documento.add(new Paragraph(reporte.titulo(), TITULO));
			for (String linea : reporte.encabezado()) {
				documento.add(new Paragraph(linea, NORMAL));
			}
			for (Seccion seccion : reporte.secciones()) {
				seccion(documento, seccion);
			}
		} catch (DocumentException e) {
			throw new IllegalStateException("No se pudo generar el PDF", e);
		} finally {
			if (documento.isOpen()) {
				documento.close();
			}
		}
		return salida.toByteArray();
	}

	private static void seccion(Document documento, Seccion seccion) {
		Paragraph titulo = new Paragraph(seccion.titulo(), SUBTITULO);
		titulo.setSpacingBefore(14);
		titulo.setSpacingAfter(6);
		documento.add(titulo);
		if (seccion.filas().isEmpty()) {
			documento.add(new Paragraph("Sin datos en el período.", NORMAL));
			return;
		}
		PdfPTable tabla = new PdfPTable(seccion.columnas().size());
		tabla.setWidthPercentage(100);
		tabla.setHeaderRows(1);
		tabla.setComplete(false);
		for (String columna : seccion.columnas()) {
			PdfPCell celda = new PdfPCell(new Phrase(columna, ENCABEZADO));
			celda.setBackgroundColor(GRIS);
			tabla.addCell(celda);
		}
		int escritas = 0;
		for (List<Object> fila : seccion.filas()) {
			for (Object valor : fila) {
				PdfPCell celda = new PdfPCell(new Phrase(valor == null ? "" : valor.toString(), NORMAL));
				if (valor instanceof Number) {
					celda.setHorizontalAlignment(Element.ALIGN_RIGHT);
				}
				tabla.addCell(celda);
			}
			if (++escritas % FILAS_POR_PARTE == 0) {
				documento.add(tabla);
			}
		}
		tabla.setComplete(true);
		documento.add(tabla);
	}
}
