package co.edu.unbosque.marketplace.pagos.infraestructura;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import co.edu.unbosque.marketplace.pagos.dominio.PasarelaPagos;
import co.edu.unbosque.marketplace.pagos.dominio.ResultadoPago;
import co.edu.unbosque.marketplace.pagos.dominio.SolicitudCobro;

/**
 * Adaptador simulado (ADR-006): aprueba todos los cobros y los guarda en memoria. Es el proveedor
 * por defecto en desarrollo y en las pruebas; en producción se puede usar {@link StripeAdapter}
 * con {@code marketplace.pagos.proveedor=stripe}. Lo crea {@link PagosConfig}.
 */
public class PasarelaSimulada implements PasarelaPagos {

	private final Map<String, String> preparados = new ConcurrentHashMap<>();
	private final Set<String> cobrados = ConcurrentHashMap.newKeySet();
	private final Set<String> devueltos = ConcurrentHashMap.newKeySet();

	@Override
	public String preparar(SolicitudCobro solicitud) {
		return preparados.computeIfAbsent(solicitud.claveIdempotencia(), clave -> "sim_" + UUID.randomUUID());
	}

	@Override
	public ResultadoPago cobrar(String referencia, SolicitudCobro solicitud) {
		cobrados.add(referencia);
		return ResultadoPago.aprobado(referencia);
	}

	@Override
	public ResultadoPago consultar(String referencia) {
		return cobrados.contains(referencia) ? ResultadoPago.aprobado(referencia) : ResultadoPago.noCobrado();
	}

	@Override
	public void devolver(String referencia, BigDecimal monto, String claveIdempotencia, int intento) {
		// Un conjunto concurrente: una devolución por cobro aunque lleguen varios intentos a la vez
		devueltos.add(referencia);
	}

	/** Cobros hechos; lo usan las pruebas para comprobar que un reintento no cobra dos veces. */
	public int cantidadCobros() {
		return cobrados.size();
	}

	public int cantidadDevoluciones() {
		return devueltos.size();
	}
}
