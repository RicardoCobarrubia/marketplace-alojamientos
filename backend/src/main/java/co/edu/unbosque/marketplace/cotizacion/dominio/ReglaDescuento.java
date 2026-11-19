package co.edu.unbosque.marketplace.cotizacion.dominio;

import java.math.BigDecimal;

/**
 * Un tipo de descuento de la cotización (patrón Strategy, MA-65 escenario 3).
 * <p>
 * Para agregar un descuento nuevo basta con crear otra implementación registrada como bean:
 * la calculadora recibe todas las reglas y no se modifica.
 */
public interface ReglaDescuento {

	/** Nombre que se muestra en el desglose, por ejemplo "Descuento por estadía larga". */
	String nombre();

	/**
	 * Valor a descontar del subtotal. Cero si no aplica. Nunca negativo ni mayor que el
	 * subtotal.
	 */
	BigDecimal calcular(long noches, BigDecimal subtotal, ConfiguracionCotizacion configuracion);
}
