package co.edu.unbosque.marketplace.resenas.aplicacion;

import java.time.Instant;

import co.edu.unbosque.marketplace.resenas.dominio.Resena;

/** Una reseña completa, para su autor y para el administrador (MA-78, MA-79). */
public record ResenaDTO(Long id, Long reservaId, Long alojamientoId, Long huespedId, int calificacion,
		String comentario, Instant creadaEn, boolean oculta, String motivoOcultamiento) {

	public static ResenaDTO desde(Resena resena) {
		return new ResenaDTO(resena.getId(), resena.getReservaId(), resena.getAlojamientoId(), resena.getHuespedId(),
				resena.getCalificacion(), resena.getComentario(), resena.getCreadaEn(), resena.isOculta(),
				resena.getMotivoOcultamiento());
	}
}
