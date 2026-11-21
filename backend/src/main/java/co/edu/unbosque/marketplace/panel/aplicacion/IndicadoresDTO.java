package co.edu.unbosque.marketplace.panel.aplicacion;

import java.time.Instant;
import java.util.Map;

/**
 * Indicadores de la plataforma (MA-83, escenario 1). Los mapas traen todos los roles y estados,
 * con cero los que no tienen filas.
 */
public record IndicadoresDTO(long usuarios, Map<String, Long> usuariosPorRol, long alojamientosActivos,
		long reservas, Map<String, Long> reservasPorEstado, Instant generadoEn) {
}
