package co.edu.unbosque.marketplace.panel.aplicacion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Un rango de fechas del panel y de los reportes, con ambos extremos incluidos (MA-84, MA-85).
 *
 * @param desde primer día
 * @param hasta último día
 */
public record Periodo(LocalDate desde, LocalDate hasta) {

	/** Cuántos días cubre, contando los dos extremos. */
	public long dias() {
		return ChronoUnit.DAYS.between(desde, hasta) + 1;
	}

	/** El primer instante del período en la zona horaria dada. */
	public Instant inicio(ZoneId zona) {
		return desde.atStartOfDay(zona).toInstant();
	}

	/** El primer instante después del período: el rango es {@code [inicio, fin)}. */
	public Instant fin(ZoneId zona) {
		return hasta.plusDays(1).atStartOfDay(zona).toInstant();
	}
}
