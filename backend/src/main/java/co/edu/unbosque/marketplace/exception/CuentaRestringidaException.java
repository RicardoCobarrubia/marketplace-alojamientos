package co.edu.unbosque.marketplace.exception;

import org.springframework.http.HttpStatus;

/** Un administrador restringió la cuenta: no puede iniciar sesión (MA-48, escenario 2). HTTP 403. */
public class CuentaRestringidaException extends ExcepcionMarketplace {

	private static final long serialVersionUID = 1L;

	public CuentaRestringidaException() {
		super(HttpStatus.FORBIDDEN, "CUENTA_RESTRINGIDA", "Cuenta restringida",
				"Tu cuenta está restringida. Comunícate con el equipo de soporte.");
	}
}
