package co.edu.unbosque.marketplace.exception;

/**
 * Se lanza cuando se busca un recurso que no existe, por ejemplo una reserva o un
 * alojamiento con un id desconocido. Se responde con HTTP 404.
 */
public class RecursoNoEncontradoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RecursoNoEncontradoException(String mensaje) {
		super(mensaje);
	}
}
