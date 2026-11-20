package co.edu.unbosque.marketplace.reservas.dominio;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Política flexible (MA-73, escenario 1): devolución total si se cancela con la anticipación
 * configurada o más (48 horas); si no, el porcentaje configurado (50 %).
 */
@Component
public class DevolucionFlexible implements ReglaDevolucion {

	private static final BigDecimal CIEN = BigDecimal.valueOf(100);

	@Override
	public String codigo() {
		return "FLEXIBLE";
	}

	@Override
	public BigDecimal porcentaje(Map<String, Object> parametros, Duration anticipacion) {
		long horas = numero(parametros, "horasAntesParaReembolsoTotal").longValueExact();
		return anticipacion.compareTo(Duration.ofHours(horas)) >= 0
				? CIEN
				: numero(parametros, "porcentajeReembolsoDespues");
	}

	private static BigDecimal numero(Map<String, Object> parametros, String clave) {
		if (!(parametros.get(clave) instanceof Number valor)) {
			throw new IllegalStateException("La política FLEXIBLE no tiene el parámetro numérico " + clave + ".");
		}
		return new BigDecimal(valor.toString());
	}
}
