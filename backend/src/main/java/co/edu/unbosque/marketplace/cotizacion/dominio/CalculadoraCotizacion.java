package co.edu.unbosque.marketplace.cotizacion.dominio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.ServicioAdicional;
import co.edu.unbosque.marketplace.cotizacion.dominio.Cotizacion.LineaDescuento;
import co.edu.unbosque.marketplace.cotizacion.dominio.Cotizacion.LineaServicio;

/**
 * Calcula la cotización (MA-63, MA-64). Es una función pura: no consulta la base de datos ni
 * el reloj, así que la misma entrada da siempre el mismo total.
 * <p>
 * Los descuentos llegan como una lista de {@link ReglaDescuento}: agregar uno nuevo no cambia
 * esta clase (MA-65, escenario 3).
 */
@Component
public class CalculadoraCotizacion {

	private final List<ReglaDescuento> reglasDescuento;

	public CalculadoraCotizacion(List<ReglaDescuento> reglasDescuento) {
		this.reglasDescuento = reglasDescuento.stream()
				.sorted(Comparator.comparing(ReglaDescuento::nombre))
				.toList();
	}

	public Cotizacion calcular(Alojamiento alojamiento, long noches, List<ServicioAdicional> servicios,
			ConfiguracionCotizacion configuracion) {
		if (noches <= 0) {
			throw new IllegalArgumentException("La estadía debe tener al menos una noche.");
		}
		BigDecimal precioNoche = Dinero.redondear(alojamiento.getPrecioNoche());
		BigDecimal subtotal = Dinero.redondear(precioNoche.multiply(BigDecimal.valueOf(noches)));

		List<LineaDescuento> descuentos = new ArrayList<>();
		BigDecimal totalDescuentos = BigDecimal.ZERO;
		for (ReglaDescuento regla : reglasDescuento) {
			BigDecimal valor = regla.calcular(noches, subtotal, configuracion);
			if (valor.signum() > 0) {
				// Los descuentos juntos nunca dejan el subtotal en negativo
				valor = valor.min(subtotal.subtract(totalDescuentos));
				descuentos.add(new LineaDescuento(regla.nombre(), valor));
				totalDescuentos = totalDescuentos.add(valor);
			}
		}

		List<LineaServicio> lineas = servicios.stream()
				.sorted(Comparator.comparing(ServicioAdicional::getId, Comparator.nullsLast(Comparator.naturalOrder())))
				.map(servicio -> linea(servicio, noches))
				.toList();
		BigDecimal totalServicios = lineas.stream().map(LineaServicio::valor).reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal limpieza = Dinero.redondear(alojamiento.getTarifaLimpieza());
		BigDecimal base = subtotal.subtract(totalDescuentos).add(limpieza).add(totalServicios);
		BigDecimal tarifaServicio = Dinero.porcentaje(base, configuracion.getPorcentajeTarifaServicio());

		return new Cotizacion(noches, precioNoche, subtotal, descuentos, Dinero.redondear(totalDescuentos), limpieza,
				lineas, Dinero.redondear(totalServicios), configuracion.getPorcentajeTarifaServicio(), tarifaServicio,
				Dinero.redondear(base.add(tarifaServicio)));
	}

	private static LineaServicio linea(ServicioAdicional servicio, long noches) {
		long cantidad = servicio.getModalidad() == ServicioAdicional.Modalidad.POR_NOCHE ? noches : 1;
		BigDecimal costo = Dinero.redondear(servicio.getCosto());
		return new LineaServicio(servicio.getId(), servicio.getNombre(), servicio.getModalidad(), costo, cantidad,
				Dinero.redondear(costo.multiply(BigDecimal.valueOf(cantidad))));
	}
}
