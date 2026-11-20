package co.edu.unbosque.marketplace.reservas.dominio;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * Cuánto se devuelve al cancelar según un tipo de política de cancelación (patrón Strategy,
 * MA-73).
 * <p>
 * Para agregar una política nueva basta con crear otra implementación registrada como bean, con
 * el código de la fila de {@code politica_cancelacion}: el flujo de cancelación no se modifica
 * (MA-73, escenario 3).
 */
public interface ReglaDevolucion {

	/** Código de la política, igual al de {@code politica_cancelacion.codigo}. */
	String codigo();

	/**
	 * Porcentaje del total que se devuelve, de 0 a 100.
	 *
	 * @param parametros los parámetros de la política copiados al reservar
	 * @param anticipacion tiempo entre la cancelación y el día de llegada
	 */
	BigDecimal porcentaje(Map<String, Object> parametros, Duration anticipacion);
}
