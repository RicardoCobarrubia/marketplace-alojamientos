package co.edu.unbosque.marketplace.resenas.aplicacion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import co.edu.unbosque.marketplace.resenas.dominio.Resena;

/**
 * Las reseñas visibles de un alojamiento con su promedio, para el detalle público (MA-78,
 * escenario 3). No muestra quién escribió cada una.
 *
 * @param promedio de 1.0 a 5.0 con un decimal; nulo si no hay reseñas visibles
 */
public record ResenasAlojamientoDTO(Long alojamientoId, BigDecimal promedio, int cantidad,
		List<ResenaPublica> resenas) {

	/** Una reseña tal como la ve cualquier visitante. */
	public record ResenaPublica(Long id, int calificacion, String comentario, Instant creadaEn) {

		static ResenaPublica desde(Resena resena) {
			return new ResenaPublica(resena.getId(), resena.getCalificacion(), resena.getComentario(),
					resena.getCreadaEn());
		}
	}
}
