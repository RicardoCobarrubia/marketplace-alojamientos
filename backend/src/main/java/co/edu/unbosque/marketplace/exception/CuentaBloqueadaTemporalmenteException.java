package co.edu.unbosque.marketplace.exception;

import org.springframework.http.HttpStatus;

/** Demasiados intentos fallidos seguidos: hay que esperar antes de volver a intentar. HTTP 429. */
public class CuentaBloqueadaTemporalmenteException extends ExcepcionMarketplace {

	private static final long serialVersionUID = 1L;

	public CuentaBloqueadaTemporalmenteException() {
		super(HttpStatus.TOO_MANY_REQUESTS, "CUENTA_BLOQUEADA_TEMPORALMENTE", "Demasiados intentos",
				"Hubo demasiados intentos fallidos. Espera unos minutos y vuelve a intentarlo.");
	}
}
