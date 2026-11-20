package co.edu.unbosque.marketplace.notificaciones.dominio;

/** El servidor de correo no aceptó el mensaje o no respondió a tiempo: se reintentará. */
public class CorreoNoEnviadoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CorreoNoEnviadoException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}
}
