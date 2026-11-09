package co.edu.unbosque.marketplace.alojamientos.aplicacion;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import co.edu.unbosque.marketplace.alojamientos.dominio.ServicioAdicional;
import co.edu.unbosque.marketplace.alojamientos.dominio.ServicioAdicional.Modalidad;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** DTO de servicio adicional, por ejemplo desayuno o traslado (MA-55). */
public record ServicioAdicionalDTO(

		@JsonProperty(access = Access.READ_ONLY)
		Long id,

		@NotBlank(message = "El nombre es obligatorio.")
		@Size(max = 100, message = "El nombre admite máximo 100 caracteres.")
		String nombre,

		@NotNull(message = "El costo es obligatorio.")
		@Positive(message = "El costo debe ser positivo.")
		@Digits(integer = 10, fraction = 2, message = "El costo admite máximo 2 decimales.")
		BigDecimal costo,

		@NotNull(message = "La modalidad es obligatoria: POR_ESTADIA o POR_NOCHE.")
		Modalidad modalidad) {

	public static ServicioAdicionalDTO desde(ServicioAdicional servicio) {
		return new ServicioAdicionalDTO(servicio.getId(), servicio.getNombre(), servicio.getCosto(),
				servicio.getModalidad());
	}
}
