package co.edu.unbosque.marketplace.disponibilidad.aplicacion;

import java.time.LocalDate;

/**
 * Resultado de consultar si un alojamiento se puede reservar en unas fechas (MA-59).
 * Si no está disponible, {@code motivo} dice por qué: NO_ACTIVO, FUERA_DE_PERIODO u
 * OCUPADO.
 */
public record DisponibilidadDTO(
		Long alojamientoId,
		LocalDate llegada,
		LocalDate salida,
		long noches,
		boolean disponible,
		String motivo) {
}
