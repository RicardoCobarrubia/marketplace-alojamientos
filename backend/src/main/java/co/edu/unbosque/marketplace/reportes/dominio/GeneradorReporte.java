package co.edu.unbosque.marketplace.reportes.dominio;

/** Convierte el contenido de un reporte en un archivo de un formato (MA-85). */
public interface GeneradorReporte {

	FormatoReporte formato();

	byte[] generar(Reporte reporte);
}
