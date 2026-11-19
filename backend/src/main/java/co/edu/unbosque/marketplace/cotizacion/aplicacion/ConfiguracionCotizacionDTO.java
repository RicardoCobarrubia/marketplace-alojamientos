package co.edu.unbosque.marketplace.cotizacion.aplicacion;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import co.edu.unbosque.marketplace.cotizacion.dominio.ConfiguracionCotizacion;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Reglas de la cotización que configura el administrador (MA-65). */
public record ConfiguracionCotizacionDTO(

		@NotNull(message = "La tarifa de servicio es obligatoria.")
		@DecimalMin(value = "0", message = "La tarifa de servicio no puede ser negativa.")
		@DecimalMax(value = "100", message = "La tarifa de servicio no puede superar el 100 %.")
		@Digits(integer = 3, fraction = 2, message = "La tarifa de servicio admite máximo 2 decimales.")
		BigDecimal porcentajeTarifaServicio,

		@NotNull(message = "El descuento por estadía larga es obligatorio.")
		@DecimalMin(value = "0", message = "El descuento no puede ser negativo.")
		@DecimalMax(value = "100", message = "El descuento no puede superar el 100 %.")
		@Digits(integer = 3, fraction = 2, message = "El descuento admite máximo 2 decimales.")
		BigDecimal porcentajeDescuentoEstadiaLarga,

		@NotNull(message = "El mínimo de noches es obligatorio.")
		@Min(value = 1, message = "El mínimo de noches debe ser al menos 1.")
		@Max(value = 365, message = "El mínimo de noches no puede superar 365.")
		Integer minimoNochesDescuento,

		@JsonProperty(access = Access.READ_ONLY)
		Instant actualizadoEn) {

	public static ConfiguracionCotizacionDTO desde(ConfiguracionCotizacion configuracion) {
		return new ConfiguracionCotizacionDTO(configuracion.getPorcentajeTarifaServicio(),
				configuracion.getPorcentajeDescuentoEstadiaLarga(), configuracion.getMinimoNochesDescuento(),
				configuracion.getActualizadoEn());
	}
}
