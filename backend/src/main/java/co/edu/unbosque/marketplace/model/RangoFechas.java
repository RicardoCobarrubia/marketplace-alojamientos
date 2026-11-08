package co.edu.unbosque.marketplace.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Rango de fechas semiabierto [llegada, salida): la salida no cuenta como noche
 * (objeto de valor RangoFechas del modelo de dominio).
 */
public record RangoFechas(LocalDate llegada, LocalDate salida) {

	public RangoFechas {
		if (llegada == null || salida == null || !salida.isAfter(llegada)) {
			throw new IllegalArgumentException("La salida debe ser posterior a la llegada.");
		}
	}

	public long noches() {
		return ChronoUnit.DAYS.between(llegada, salida);
	}

	/** Comparten al menos una noche. Un rango que termina el día que empieza el otro no se cruza. */
	public boolean seCruzaCon(RangoFechas otro) {
		return llegada.isBefore(otro.salida) && otro.llegada.isBefore(salida);
	}

	/** El otro rango cabe completo dentro de este. */
	public boolean contiene(RangoFechas otro) {
		return !otro.llegada.isBefore(llegada) && !otro.salida.isAfter(salida);
	}
}
