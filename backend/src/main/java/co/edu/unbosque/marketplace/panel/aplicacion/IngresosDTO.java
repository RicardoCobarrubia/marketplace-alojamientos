package co.edu.unbosque.marketplace.panel.aplicacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Ingresos estimados y demanda por ciudad en un rango de fechas (MA-84). Cuentan las reservas
 * CONFIRMADA y COMPLETADA creadas entre {@code desde} y {@code hasta}, ambos incluidos, en la hora
 * de Colombia.
 *
 * @param ingresosEstimados suma de las tarifas de servicio de esas reservas
 * @param reservas cuántas reservas suman
 * @param ciudadesConMasReservas las 5 primeras, de más a menos reservas
 */
public record IngresosDTO(LocalDate desde, LocalDate hasta, BigDecimal ingresosEstimados, long reservas,
		List<CiudadReservas> ciudadesConMasReservas) {

	public record CiudadReservas(Long ciudadId, String ciudad, String departamento, long reservas) {
	}
}
