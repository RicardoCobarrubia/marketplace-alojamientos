package co.edu.unbosque.marketplace.cotizacion.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Redondeo único de la cotización: dos decimales, mitad hacia arriba. Así la misma entrada da siempre el mismo total. */
public final class Dinero {

	private static final BigDecimal CIEN = BigDecimal.valueOf(100);

	private Dinero() {
	}

	public static BigDecimal redondear(BigDecimal valor) {
		return valor.setScale(2, RoundingMode.HALF_UP);
	}

	public static BigDecimal porcentaje(BigDecimal base, BigDecimal porcentaje) {
		return redondear(base.multiply(porcentaje).divide(CIEN, 6, RoundingMode.HALF_UP));
	}
}
