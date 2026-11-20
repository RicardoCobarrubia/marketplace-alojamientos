package co.edu.unbosque.marketplace.reservas.presentacion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
		/** No responde al preparar: nunca hubo cobro. */
		SIN_CONEXION,
		/** Prepara, pero no responde al cobrar y no alcanzó a cobrar. */
		TIMEOUT_SIN_COBRO,
		/** Prepara, no responde al cobrar, pero sí cobró. */
		TIMEOUT_COBRADO,
		/** Prepara, y después no responde a nada, ni siquiera a la consulta. */
		CAIDA
	}

	private volatile Modo modo = Modo.APROBAR;
	private final Map<String, String> preparados = new ConcurrentHashMap<>();
	private final Set<String> cobrados = ConcurrentHashMap.newKeySet();
	private final Set<String> devoluciones = ConcurrentHashMap.newKeySet();
	private final Map<String, BigDecimal> montosDevueltos = new ConcurrentHashMap<>();
	private final List<String> llamadasDevolucion = new CopyOnWriteArrayList<>();
	private volatile CountDownLatch dentroDeDevolucion;
	private volatile CountDownLatch soltarDevolucion;
	private final AtomicInteger llamadasCobro = new AtomicInteger();

	public void modo(Modo nuevo) {
		this.modo = nuevo;
	}

	public void reiniciar() {
		modo = Modo.APROBAR;
		preparados.clear();
		cobrados.clear();
		devoluciones.clear();
		montosDevueltos.clear();
		llamadasDevolucion.clear();
		dentroDeDevolucion = null;
		soltarDevolucion = null;
		llamadasCobro.set(0);
	}

	public int cobros() {
		return cobrados.size();
	}

	public int llamadasCobro() {
		return llamadasCobro.get();
	}

	public int devoluciones() {
		return devoluciones.size();
	}

	/** Cada llamada a devolver, en orden: "referencia monto clave intento". */
	public List<String> llamadasDevolucion() {
		return List.copyOf(llamadasDevolucion);
	}

	/**
	 * La siguiente devolución avisa con {@code dentro} que llegó a la pasarela y espera
	 * {@code soltar} antes de responder.
	 */
	public void detenerSiguienteDevolucion(CountDownLatch dentro, CountDownLatch soltar) {
		this.dentroDeDevolucion = dentro;
		this.soltarDevolucion = soltar;
	}

	/** Lo último que se pidió devolver de un cobro. */
	public BigDecimal montoDevuelto(String referencia) {
		return montosDevueltos.get(referencia);
	}

	@Override
	public String preparar(SolicitudCobro solicitud) {
		if (modo == Modo.SIN_CONEXION) {
			throw new PasarelaNoDisponibleException("Sin conexión simulada");
		}
		return preparados.computeIfAbsent(solicitud.claveIdempotencia(), c -> "prueba_" + UUID.randomUUID());
	}

	@Override
	public ResultadoPago cobrar(String referencia, SolicitudCobro solicitud) {
		llamadasCobro.incrementAndGet();
		return switch (modo) {
			case APROBAR -> {
				cobrados.add(referencia);
				yield ResultadoPago.aprobado(referencia);
			}
			case RECHAZAR -> ResultadoPago.rechazado(referencia, "Fondos insuficientes");
			case TIMEOUT_COBRADO -> {
				cobrados.add(referencia);
				throw new PasarelaNoDisponibleException("Timeout simulado");
			}
			case SIN_CONEXION, TIMEOUT_SIN_COBRO, CAIDA -> throw new PasarelaNoDisponibleException("Timeout simulado");
		};
	}

	@Override
	public ResultadoPago consultar(String referencia) {
		if (modo == Modo.CAIDA || modo == Modo.SIN_CONEXION) {
			throw new PasarelaNoDisponibleException("Caída simulada");
		}
		return cobrados.contains(referencia) ? ResultadoPago.aprobado(referencia) : ResultadoPago.noCobrado();
	}

	@Override
	public void devolver(String referencia, BigDecimal monto, String claveIdempotencia, int intento) {
		llamadasDevolucion.add(referencia + " " + monto.stripTrailingZeros().toPlainString() + " "
				+ claveIdempotencia + " " + intento);
		CountDownLatch dentro = dentroDeDevolucion;
		CountDownLatch soltar = soltarDevolucion;
		if (dentro != null && soltar != null) {
			dentroDeDevolucion = null;
			dentro.countDown();
			esperar(soltar);
		}
		if (modo == Modo.CAIDA || modo == Modo.SIN_CONEXION) {
			throw new PasarelaNoDisponibleException("Caída simulada");
		}
		devoluciones.add(referencia);
		montosDevueltos.put(referencia, monto);
	}

	private static void esperar(CountDownLatch soltar) {
		try {
			if (!soltar.await(30, TimeUnit.SECONDS)) {
				throw new IllegalStateException("La prueba no soltó la devolución.");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new PasarelaNoDisponibleException("Interrumpida", e);
		}
	}
}
