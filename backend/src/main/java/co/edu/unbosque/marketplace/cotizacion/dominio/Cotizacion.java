package co.edu.unbosque.marketplace.cotizacion.dominio;

import java.math.BigDecimal;
import java.util.List;

import co.edu.unbosque.marketplace.alojamientos.dominio.ServicioAdicional.Modalidad;

/**
 * Costo de una estadía con su desglose (MA-63): subtotal (noches × precio por noche),
 * descuentos, limpieza, servicios adicionales, tarifa de servicio y total.
 * <p>
 * La tarifa de servicio es un porcentaje sobre la suma anterior:
 * {@code subtotal - descuentos + limpieza + servicios}.
 */
public record Cotizacion(
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

	public Cotizacion {
		descuentos = List.copyOf(descuentos);
		servicios = List.copyOf(servicios);
	}

	/** Un descuento aplicado, por ejemplo el de estadía larga. */
	public record LineaDescuento(String nombre, BigDecimal valor) {
	}

	/** Un servicio adicional elegido: por estadía se cobra una vez, por noche se multiplica por las noches. */
	public record LineaServicio(Long servicioId, String nombre, Modalidad modalidad, BigDecimal costoUnitario,
			long cantidad, BigDecimal valor) {
	}
}
