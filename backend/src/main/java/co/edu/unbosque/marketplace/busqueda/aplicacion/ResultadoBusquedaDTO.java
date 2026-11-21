package co.edu.unbosque.marketplace.busqueda.aplicacion;

import java.util.List;

import co.edu.unbosque.marketplace.alojamientos.aplicacion.AlojamientoDTO;

/**
 * Una página de resultados de la búsqueda (MA-61), del más barato por noche al más caro.
 *
 * @param mensaje texto para mostrar cuando no hay resultados (escenario 2); nulo si hay
 */
public record ResultadoBusquedaDTO(List<AlojamientoDTO> alojamientos, int pagina, int tamano, long total,
		int totalPaginas, String mensaje) {
}
