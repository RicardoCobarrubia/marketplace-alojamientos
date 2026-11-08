package co.edu.unbosque.marketplace.exception;

import org.springframework.http.HttpStatus;

/**
 * El usuario tiene el rol correcto pero no es dueño del recurso, por ejemplo un anfitrión que
 * intenta editar el alojamiento de otro (política por recurso, ADR-009). HTTP 403.
 */
public class AccesoDenegadoException extends ExcepcionMarketplace {

	private static final long serialVersionUID = 1L;

	public AccesoDenegadoException(String mensaje) {
		super(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO", "Acceso denegado", mensaje);
	}
}
