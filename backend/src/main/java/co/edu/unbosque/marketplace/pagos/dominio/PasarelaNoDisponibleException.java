package co.edu.unbosque.marketplace.pagos.dominio;

/** La pasarela no respondió a tiempo o falló: no se sabe si cobró (ADR-004). */
public class PasarelaNoDisponibleException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final boolean puedeSeguirEnCurso;

	/** Sin respuesta (timeout, conexión caída): la petición puede seguir procesándose en la pasarela. */
	public PasarelaNoDisponibleException(String mensaje, Throwable causa) {
		this(mensaje, causa, true);
	}

	/** Sin respuesta (timeout, conexión caída): la petición puede seguir procesándose en la pasarela. */
	public PasarelaNoDisponibleException(String mensaje) {
		this(mensaje, null, true);
	}

	private PasarelaNoDisponibleException(String mensaje, Throwable causa, boolean puedeSeguirEnCurso) {
		super(mensaje, causa);
		this.puedeSeguirEnCurso = puedeSeguirEnCurso;
	}

	/** La pasarela respondió con un error: la petición terminó y no queda nada en curso. */
	public static PasarelaNoDisponibleException conRespuesta(String mensaje) {
		return new PasarelaNoDisponibleException(mensaje, null, false);
	}

	/**
	 * Si la petición puede seguir procesándose en la pasarela. En ese caso un reintento debe
	 * usar la misma clave de idempotencia, para que la pasarela no la repita; si no, conviene una
	 * clave nueva, porque la pasarela repetiría la respuesta de error guardada con la anterior.
	 */
	public boolean puedeSeguirEnCurso() {
		return puedeSeguirEnCurso;
	}
}
