package co.edu.unbosque.marketplace.comun.excepcion;

import org.springframework.http.HttpStatus;

/** La petición tiene un valor que no se permite, por ejemplo registrarse con rol Administrador. HTTP 400. */
public class SolicitudInvalidaException extends ExcepcionMarketplace {

	private static final long serialVersionUID = 1L;

	public SolicitudInvalidaException(String codigo, String mensaje) {
		super(HttpStatus.BAD_REQUEST, codigo, "Solicitud inválida", mensaje);
	}
}
