package co.edu.unbosque.marketplace.exception;

import org.springframework.http.HttpStatus;

/**
 * Se lanza cuando se busca un recurso que no existe, por ejemplo una reserva o un
 * alojamiento con un id desconocido. Se responde con HTTP 404.
 */
public class RecursoNoEncontradoException extends ExcepcionMarketplace {

	private static final long serialVersionUID = 1L;

	public RecursoNoEncontradoException(String mensaje) {
		super(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO", "Recurso no encontrado", mensaje);
	}
}
