package co.edu.unbosque.marketplace.reservas.presentacion;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import co.edu.unbosque.marketplace.pagos.dominio.PasarelaNoDisponibleException;
import co.edu.unbosque.marketplace.pagos.dominio.PasarelaPagos;
import co.edu.unbosque.marketplace.pagos.dominio.ResultadoPago;
import co.edu.unbosque.marketplace.pagos.dominio.SolicitudCobro;

/** Pasarela para inyectar fallos en las pruebas de integración (MA-68, escenario 3). */
public class PasarelaDePrueba implements PasarelaPagos {

	public enum Modo {
		APROBAR,
		RECHAZAR,
		/** No responde y no alcanzó a cobrar. */
		TIMEOUT_SIN_COBRO,
		/** No responde, pero sí cobró. */
		TIMEOUT_COBRADO,
		/** No responde a nada, ni siquiera a la consulta. */
		CAIDA
	}

	private volatile Modo modo = Modo.APROBAR;
	private final Map<String, String> cobros = new ConcurrentHashMap<>();
	private final Set<String> devoluciones = ConcurrentHashMap.newKeySet();
	private final AtomicInteger llamadasCobro = new AtomicInteger();

	public void modo(Modo nuevo) {
		this.modo = nuevo;
	}

	public void reiniciar() {
		modo = Modo.APROBAR;
		cobros.clear();
		devoluciones.clear();
		llamadasCobro.set(0);
	}

	public int cobros() {
		return cobros.size();
	}

	public int llamadasCobro() {
		return llamadasCobro.get();
	}

	public int devoluciones() {
		return devoluciones.size();
	}

	@Override
	public ResultadoPago cobrar(SolicitudCobro solicitud) {
		llamadasCobro.incrementAndGet();
		return switch (modo) {
			case APROBAR -> ResultadoPago.aprobado(cobrarUnaVez(solicitud.claveIdempotencia()));
			case RECHAZAR -> ResultadoPago.rechazado(null, "Fondos insuficientes");
			case TIMEOUT_COBRADO -> {
				cobrarUnaVez(solicitud.claveIdempotencia());
				throw new PasarelaNoDisponibleException("Timeout simulado");
			}
			case TIMEOUT_SIN_COBRO, CAIDA -> throw new PasarelaNoDisponibleException("Timeout simulado");
		};
	}

	@Override
	public ResultadoPago consultar(String claveIdempotencia, BigDecimal monto) {
		if (modo == Modo.CAIDA) {
			throw new PasarelaNoDisponibleException("Caída simulada");
		}
		String referencia = cobros.get(claveIdempotencia);
		return referencia != null ? ResultadoPago.aprobado(referencia) : ResultadoPago.noCobrado();
	}

	@Override
	public void devolver(String referencia, BigDecimal monto, String claveIdempotencia) {
		if (modo == Modo.CAIDA) {
			throw new PasarelaNoDisponibleException("Caída simulada");
		}
		devoluciones.add(claveIdempotencia);
	}

	private String cobrarUnaVez(String clave) {
		return cobros.computeIfAbsent(clave, c -> "prueba_" + UUID.randomUUID());
	}
}
