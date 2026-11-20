package co.edu.unbosque.marketplace.reservas.dominio;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import co.edu.unbosque.marketplace.cotizacion.dominio.Dinero;

/**
 * Calcula el valor que se devuelve al cancelar una reserva (MA-73). Recibe todas las
 * {@link ReglaDevolucion} y elige la de la política copiada en la reserva: agregar una política
 * no cambia esta clase ni el flujo de cancelación.
 */
@Component
public class CalculadoraDevolucion {

	private static final BigDecimal CIEN = BigDecimal.valueOf(100);

	private final Map<String, ReglaDevolucion> reglas;

	public CalculadoraDevolucion(List<ReglaDevolucion> reglas) {
		this.reglas = reglas.stream().collect(Collectors.toUnmodifiableMap(ReglaDevolucion::codigo,
				Function.identity(), (una, otra) -> {
					throw new IllegalStateException("Dos reglas de devolución con el código " + una.codigo());
				}));
	}

	/**
	 * Valor a devolver, con dos decimales.
	 *
	 * @param porAdministrador si cancela el administrador se devuelve el 100 % (MA-73, escenario 1)
	 */
	public BigDecimal calcular(String codigoPolitica, Map<String, Object> parametros, BigDecimal total,
			Duration anticipacion, boolean porAdministrador) {
		if (porAdministrador) {
			return Dinero.redondear(total);
		}
		ReglaDevolucion regla = reglas.get(codigoPolitica);
		if (regla == null) {
			throw new IllegalStateException("No hay regla de devolución para la política " + codigoPolitica + ".");
		}
		BigDecimal porcentaje = regla.porcentaje(parametros, anticipacion);
		if (porcentaje.signum() < 0 || porcentaje.compareTo(CIEN) > 0) {
			throw new IllegalStateException("La política " + codigoPolitica + " dio un porcentaje fuera de 0 a 100.");
		}
		return Dinero.porcentaje(total, porcentaje);
	}
}
