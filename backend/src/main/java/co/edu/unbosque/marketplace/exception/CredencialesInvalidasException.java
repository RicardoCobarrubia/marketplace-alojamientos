package co.edu.unbosque.marketplace.exception;

import org.springframework.http.HttpStatus;

/**
 * Correo o contraseña incorrectos. El mensaje es el mismo en los dos casos para no revelar
 * qué correos están registrados (MA-48, escenario 1). HTTP 401.
 */
public class CredencialesInvalidasException extends ExcepcionMarketplace {

	private static final long serialVersionUID = 1L;

	public CredencialesInvalidasException() {
		super(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "No autenticado",
				"El correo o la contraseña no son correctos.");
	}
}
