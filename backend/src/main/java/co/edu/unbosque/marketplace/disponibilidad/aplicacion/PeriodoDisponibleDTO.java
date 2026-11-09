package co.edu.unbosque.marketplace.disponibilidad.aplicacion;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import co.edu.unbosque.marketplace.disponibilidad.dominio.PeriodoDisponible;
import jakarta.validation.constraints.NotNull;

/**
 * DTO de periodo disponible (MA-58). {@code fechaFin} es el último día de salida posible.
 * Fechas en formato ISO: {@code 2026-12-20}.
 */
public record PeriodoDisponibleDTO(

		@JsonProperty(access = Access.READ_ONLY)
		Long id,

		@NotNull(message = "La fecha de inicio es obligatoria.")
		LocalDate fechaInicio,

		@NotNull(message = "La fecha de fin es obligatoria.")
		LocalDate fechaFin) {

	public static PeriodoDisponibleDTO desde(PeriodoDisponible periodo) {
		return new PeriodoDisponibleDTO(periodo.getId(), periodo.getFechaInicio(), periodo.getFechaFin());
	}
}
