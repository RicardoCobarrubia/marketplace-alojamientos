package co.edu.unbosque.marketplace.pagos.dominio;

/** Respuesta de la pasarela. {@code referencia} identifica el cobro para devolverlo. */
public record ResultadoPago(EstadoPago estado, String referencia, String detalle) {

	public enum EstadoPago {
		/** Se cobró. */
		APROBADO,
		/** La pasarela rechazó el cobro (por ejemplo, tarjeta sin fondos). */
		RECHAZADO,
		/** Respuesta de una consulta: no hay cobro con esa clave. */
		NO_COBRADO
	}

	public static ResultadoPago aprobado(String referencia) {
		return new ResultadoPago(EstadoPago.APROBADO, referencia, null);
	}

	public static ResultadoPago rechazado(String referencia, String detalle) {
		return new ResultadoPago(EstadoPago.RECHAZADO, referencia, detalle);
	}

	public static ResultadoPago noCobrado() {
		return new ResultadoPago(EstadoPago.NO_COBRADO, null, null);
	}

	public boolean aprobado() {
		return estado == EstadoPago.APROBADO;
	}
}
