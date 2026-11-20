package co.edu.unbosque.marketplace.resenas.aplicacion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Lo que envía el huésped al reseñar su estadía (MA-77). */
public record NuevaResenaDTO(

		@NotNull(message = "La calificación es obligatoria.")
		@Min(value = 1, message = "La calificación va de 1 a 5.")
		@Max(value = 5, message = "La calificación va de 1 a 5.")
		Integer calificacion,

		@NotBlank(message = "El comentario es obligatorio.")
		@Size(min = 10, max = 1000, message = "El comentario debe tener entre 10 y 1000 caracteres.")
		String comentario) {
}
