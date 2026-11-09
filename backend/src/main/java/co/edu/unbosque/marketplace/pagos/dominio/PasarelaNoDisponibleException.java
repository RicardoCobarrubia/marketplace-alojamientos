package co.edu.unbosque.marketplace.pagos.dominio;

/** La pasarela no respondió a tiempo o falló: no se sabe si cobró (ADR-004). */
public class PasarelaNoDisponibleException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PasarelaNoDisponibleException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}

	public PasarelaNoDisponibleException(String mensaje) {
		super(mensaje);
	}
}
