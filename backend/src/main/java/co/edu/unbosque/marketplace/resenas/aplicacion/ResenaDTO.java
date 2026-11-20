package co.edu.unbosque.marketplace.resenas.aplicacion;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import co.edu.unbosque.marketplace.resenas.dominio.Resena;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO de reseña: se usa para publicarla (MA-77) y para responder (MA-78, MA-79).
 * <p>
 * El huésped solo envía la calificación y el comentario; el resto lo pone el sistema. El largo
 * del comentario lo valida {@link Resena} contando caracteres como PostgreSQL. En el detalle
 * público ({@link #publica}) no van el autor, la reserva ni los datos de moderación.
 */
@JsonInclude(Include.NON_NULL)
public record ResenaDTO(

		@JsonProperty(access = Access.READ_ONLY)
		Long id,

		@JsonProperty(access = Access.READ_ONLY)
		Long reservaId,

		@JsonProperty(access = Access.READ_ONLY)
		Long alojamientoId,

		@JsonProperty(access = Access.READ_ONLY)
		Long huespedId,

		@NotNull(message = "La calificación es obligatoria.")
		@Min(value = 1, message = "La calificación va de 1 a 5.")
		@Max(value = 5, message = "La calificación va de 1 a 5.")
		Integer calificacion,

		@NotBlank(message = "El comentario es obligatorio.")
		String comentario,

		@JsonProperty(access = Access.READ_ONLY)
		Instant creadaEn,

		@JsonProperty(access = Access.READ_ONLY)
		Boolean oculta,

		@JsonProperty(access = Access.READ_ONLY)
		String motivoOcultamiento) {

	/** Completa, para su autor y para el administrador. */
	public static ResenaDTO desde(Resena resena) {
		return new ResenaDTO(resena.getId(), resena.getReservaId(), resena.getAlojamientoId(), resena.getHuespedId(),
				resena.getCalificacion(), resena.getComentario(), resena.getCreadaEn(), resena.isOculta(),
				resena.getMotivoOcultamiento());
	}

	/** Tal como la ve cualquier visitante: sin el autor, la reserva ni la moderación. */
	public static ResenaDTO publica(Resena resena) {
		return new ResenaDTO(resena.getId(), null, null, null, resena.getCalificacion(), resena.getComentario(),
				resena.getCreadaEn(), null, null);
	}
}
