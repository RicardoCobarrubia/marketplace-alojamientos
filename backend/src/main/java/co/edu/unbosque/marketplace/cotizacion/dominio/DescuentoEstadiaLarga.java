package co.edu.unbosque.marketplace.cotizacion.dominio;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

/** Porcentaje del subtotal cuando la estadía alcanza el mínimo de noches configurado (RF de cotización). */
@Component
public class DescuentoEstadiaLarga implements ReglaDescuento {

	@Override
	public String nombre() {
		return "Descuento por estadía larga";
	}

	@Override
	public BigDecimal calcular(long noches, BigDecimal subtotal, ConfiguracionCotizacion configuracion) {
		if (noches < configuracion.getMinimoNochesDescuento()) {
			return BigDecimal.ZERO;
		}
		return Dinero.porcentaje(subtotal, configuracion.getPorcentajeDescuentoEstadiaLarga());
	}
}
