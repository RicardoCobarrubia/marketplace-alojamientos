package co.edu.unbosque.marketplace.moderacion.aplicacion;

/**
 * Resultado de una acción de moderación.
 *
 * @param entidad {@code ALOJAMIENTO} o {@code USUARIO}
 * @param estado el estado en que quedó
 * @param reservasEnRevision cuántas reservas confirmadas pasaron a EN_REVISION
 * @param alojamientosPausados cuántos alojamientos activos pasaron a PAUSADA
 */
public record ResultadoModeracionDTO(String entidad, Long id, String estado, String motivo, int reservasEnRevision,
		int alojamientosPausados) {
}
