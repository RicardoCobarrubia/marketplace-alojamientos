package co.edu.unbosque.marketplace.pagos.infraestructura;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import co.edu.unbosque.marketplace.pagos.dominio.PasarelaPagos;
import co.edu.unbosque.marketplace.pagos.dominio.ResultadoPago;
import co.edu.unbosque.marketplace.pagos.dominio.SolicitudCobro;

/**
 * Adaptador simulado (ADR-006): aprueba todos los cobros y los guarda en memoria por clave de
 * idempotencia. Es el proveedor por defecto en desarrollo y en las pruebas; en producción se
 * usa {@link StripeAdapter} con {@code marketplace.pagos.proveedor=stripe}.
 */
@Component
@ConditionalOnProperty(name = "marketplace.pagos.proveedor", havingValue = "simulado", matchIfMissing = true)
public class PasarelaSimulada implements PasarelaPagos {

	private final Map<String, ResultadoPago> cobros = new ConcurrentHashMap<>();
	private final List<String> devoluciones = new CopyOnWriteArrayList<>();

	@Override
	public ResultadoPago cobrar(SolicitudCobro solicitud) {
		return cobros.computeIfAbsent(solicitud.claveIdempotencia(),
				clave -> ResultadoPago.aprobado("sim_" + UUID.randomUUID()));
	}

	@Override
	public ResultadoPago consultar(String claveIdempotencia, BigDecimal monto) {
		ResultadoPago cobro = cobros.get(claveIdempotencia);
		return cobro != null ? cobro : ResultadoPago.noCobrado();
	}

	@Override
	public void devolver(String referencia, BigDecimal monto, String claveIdempotencia) {
		if (!devoluciones.contains(claveIdempotencia)) {
			devoluciones.add(claveIdempotencia);
		}
	}

	/** Cobros hechos; lo usan las pruebas para comprobar que un reintento no cobra dos veces. */
	public int cantidadCobros() {
		return cobros.size();
	}

	public int cantidadDevoluciones() {
		return devoluciones.size();
	}
}
