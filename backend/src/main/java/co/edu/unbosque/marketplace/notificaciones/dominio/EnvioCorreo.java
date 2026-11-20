package co.edu.unbosque.marketplace.notificaciones.dominio;

/**
 * Puerto para enviar correos (MA-75). El adaptador SMTP está en la infraestructura; las pruebas
 * lo reemplazan por uno en memoria.
 */
public interface EnvioCorreo {

	/**
	 * @throws CorreoNoEnviadoException si el servidor de correo no lo aceptó o no respondió a
	 *         tiempo
	 */
	void enviar(String destinatario, String asunto, String cuerpo);
}
