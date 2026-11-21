package co.edu.unbosque.marketplace.busqueda.aplicacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Lo que el huésped pide al buscar (MA-61, escenario 1). Todos los filtros son opcionales: sin
 * ninguno se listan todos los alojamientos publicados. Las fechas van juntas. Llega como
 * parámetros de la URL, por ejemplo {@code ?ciudadId=2&huespedes=3&servicioIds=1&servicioIds=4}.
 */
public record CriteriosBusquedaDTO(

		Long ciudadId,

		@DateTimeFormat(iso = ISO.DATE)
		LocalDate llegada,

		@DateTimeFormat(iso = ISO.DATE)
		LocalDate salida,

		@Positive(message = "El número de huéspedes debe ser mayor que cero.")
		Integer huespedes,

		Short tipoId,

		@PositiveOrZero(message = "El precio mínimo no puede ser negativo.")
		BigDecimal precioMin,

		@PositiveOrZero(message = "El precio máximo no puede ser negativo.")
		BigDecimal precioMax,

		Set<Short> servicioIds,

		@Min(value = 0, message = "La página empieza en 0.")
		Integer pagina,

		@Min(value = 1, message = "El tamaño de página va de 1 a 50.")
		@Max(value = 50, message = "El tamaño de página va de 1 a 50.")
		Integer tamano) {
}
