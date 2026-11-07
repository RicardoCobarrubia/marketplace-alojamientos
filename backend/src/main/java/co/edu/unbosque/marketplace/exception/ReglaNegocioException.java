package co.edu.unbosque.marketplace.exception;

/**
 * Se lanza cuando una operación viola una regla del negocio, por ejemplo reservar fechas
 * ya ocupadas o pasar una reserva a un estado no permitido. Se responde con HTTP 409.
 */
public class ReglaNegocioException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ReglaNegocioException(String mensaje) {
		super(mensaje);
	}
}
