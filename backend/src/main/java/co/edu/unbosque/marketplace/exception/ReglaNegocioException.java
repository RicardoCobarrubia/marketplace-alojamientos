package co.edu.unbosque.marketplace.exception;

import org.springframework.http.HttpStatus;

/**
 * Se lanza cuando una operación viola una regla del negocio, por ejemplo reservar fechas
 * ya ocupadas o registrar un correo que ya existe. Se responde con HTTP 409.
 */
public class ReglaNegocioException extends ExcepcionMarketplace {

	private static final long serialVersionUID = 1L;

	public ReglaNegocioException(String codigo, String mensaje) {
		super(HttpStatus.CONFLICT, codigo, "Regla de negocio incumplida", mensaje);
	}
}
