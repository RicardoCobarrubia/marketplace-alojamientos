package co.edu.unbosque.marketplace.resenas.aplicacion;

import java.math.BigDecimal;
import java.util.List;

/**
 * Las reseñas visibles de un alojamiento con su promedio, para el detalle público (MA-78,
 * escenario 3). No muestra quién escribió cada una.
 *
 * @param promedio de 1.0 a 5.0 con un decimal; nulo si no hay reseñas visibles
 * @param resenas en su forma pública ({@link ResenaDTO#publica})
 */
public record ResenasAlojamientoDTO(Long alojamientoId, BigDecimal promedio, int cantidad, List<ResenaDTO> resenas) {
}
