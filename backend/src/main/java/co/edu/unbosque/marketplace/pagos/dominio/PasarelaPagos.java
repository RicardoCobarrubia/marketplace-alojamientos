package co.edu.unbosque.marketplace.pagos.dominio;

import java.math.BigDecimal;

/**
 * Puerto de la pasarela de pagos (ADR-006). Está escrito en términos del negocio: ningún caso
 * de uso conoce el proveedor, que se elige por configuración ({@code marketplace.pagos.proveedor}).
 * <p>
 * El cobro tiene dos pasos (ADR-004). {@link #preparar} registra el cobro sin cobrar y devuelve
 * su referencia, que la reserva guarda antes de {@link #cobrar}. Con esa referencia el estado
 * real siempre se puede {@link #consultar consultar}, aunque pase mucho tiempo, y si una reserva
 * no tiene referencia es seguro que nunca se cobró.
 */
public interface PasarelaPagos {

	/**
	 * Registra el cobro sin cobrar. Repetirlo con la misma clave devuelve la misma referencia.
	 *
	 * @throws PasarelaNoDisponibleException si no responde; no se cobró nada
	 */
	String preparar(SolicitudCobro solicitud);

	/**
	 * Cobra un cobro preparado. Responde APROBADO o RECHAZADO.
	 *
	 * @throws PasarelaNoDisponibleException si la pasarela no responde a tiempo o falla; en ese
	 *         caso no se sabe si cobró y hay que {@link #consultar consultar}.
	 */
	ResultadoPago cobrar(String referencia, SolicitudCobro solicitud);

	/**
	 * Estado real del cobro con esa referencia: APROBADO si se cobró, NO_COBRADO si no.
	 *
	 * @throws PasarelaNoDisponibleException si tampoco responde
	 */
	ResultadoPago consultar(String referencia);

	/**
	 * Solicita la devolución de un cobro aprobado (MA-68, escenario 2). Si ya hay una devolución
	 * de ese cobro no hace otra. {@code intento} distingue un reintento nuevo de uno anterior que
	 * terminó en error, para que el proveedor no repita la respuesta fallida.
	 *
	 * @throws PasarelaNoDisponibleException si no responde
	 */
	void devolver(String referencia, BigDecimal monto, String claveIdempotencia, int intento);
}
