package co.edu.unbosque.marketplace.pagos.dominio;

import java.math.BigDecimal;

/**
 * Puerto de la pasarela de pagos (ADR-006). Está escrito en términos del negocio: ningún caso
 * de uso conoce el proveedor, que se elige por configuración ({@code marketplace.pagos.proveedor}).
 * <p>
 * Todas las operaciones reciben una clave de idempotencia: repetir una llamada con la misma
 * clave nunca cobra ni devuelve dos veces (ADR-005).
 */
public interface PasarelaPagos {

	/**
	 * Cobra el valor de una reserva. Responde APROBADO o RECHAZADO.
	 *
	 * @throws PasarelaNoDisponibleException si la pasarela no responde a tiempo o falla; en ese
	 *         caso no se sabe si cobró y hay que {@link #consultar consultar}.
	 */
	ResultadoPago cobrar(SolicitudCobro solicitud);

	/**
	 * Estado real del cobro hecho con esa clave: APROBADO si se cobró, NO_COBRADO si no.
	 *
	 * @throws PasarelaNoDisponibleException si tampoco responde
	 */
	ResultadoPago consultar(String claveIdempotencia, BigDecimal monto);

	/**
	 * Solicita la devolución de un cobro aprobado (MA-68, escenario 2).
	 *
	 * @throws PasarelaNoDisponibleException si no responde
	 */
	void devolver(String referencia, BigDecimal monto, String claveIdempotencia);
}
