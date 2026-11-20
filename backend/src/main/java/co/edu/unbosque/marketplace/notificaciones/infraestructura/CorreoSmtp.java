package co.edu.unbosque.marketplace.notificaciones.infraestructura;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import co.edu.unbosque.marketplace.notificaciones.dominio.CorreoNoEnviadoException;
import co.edu.unbosque.marketplace.notificaciones.dominio.EnvioCorreo;

/**
 * Envía los correos por SMTP. En desarrollo llegan a Mailpit (http://localhost:8025); en
 * producción, al servidor de {@code MAIL_HOST}. Los tiempos de espera están en
 * {@code spring.mail.properties}: un servidor lento no deja el envío colgado.
 */
@Component
public class CorreoSmtp implements EnvioCorreo {

	private final JavaMailSender mailSender;
	private final String remitente;

	public CorreoSmtp(JavaMailSender mailSender, @Value("${marketplace.notificaciones.correo.remitente}") String remitente) {
		this.mailSender = mailSender;
		this.remitente = remitente;
	}

	@Override
	public void enviar(String destinatario, String asunto, String cuerpo) {
		SimpleMailMessage mensaje = new SimpleMailMessage();
		mensaje.setFrom(remitente);
		mensaje.setTo(destinatario);
		mensaje.setSubject(asunto);
		mensaje.setText(cuerpo);
		try {
			mailSender.send(mensaje);
		} catch (MailException e) {
			throw new CorreoNoEnviadoException("El servidor de correo no aceptó el mensaje.", e);
		}
	}
}
