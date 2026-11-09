package co.edu.unbosque.marketplace.cotizacion.aplicacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import co.edu.unbosque.marketplace.cotizacion.dominio.Cotizacion;
import co.edu.unbosque.marketplace.cotizacion.dominio.Cotizacion.LineaDescuento;
import co.edu.unbosque.marketplace.cotizacion.dominio.Cotizacion.LineaServicio;

/** Respuesta de la cotización con su desglose (MA-63, MA-64). */
public record CotizacionDTO(
		Long alojamientoId,
		LocalDate llegada,
		LocalDate salida,
		long noches,
		BigDecimal precioNoche,
		BigDecimal subtotal,
		List<LineaDescuento> descuentos,
		BigDecimal totalDescuentos,
		BigDecimal limpieza,
		List<LineaServicio> servicios,
		BigDecimal totalServicios,
		BigDecimal porcentajeTarifaServicio,
		BigDecimal tarifaServicio,
		BigDecimal total) {

	public static CotizacionDTO desde(Long alojamientoId, LocalDate llegada, LocalDate salida, Cotizacion cotizacion) {
		return new CotizacionDTO(alojamientoId, llegada, salida, cotizacion.noches(), cotizacion.precioNoche(),
				cotizacion.subtotal(), cotizacion.descuentos(), cotizacion.totalDescuentos(), cotizacion.limpieza(),
				cotizacion.servicios(), cotizacion.totalServicios(), cotizacion.porcentajeTarifaServicio(),
				cotizacion.tarifaServicio(), cotizacion.total());
	}
}
