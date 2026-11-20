package co.edu.unbosque.marketplace.notificaciones.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import co.edu.unbosque.marketplace.notificaciones.dominio.CorreoNoEnviadoException;

class CorreoSmtpTest {

	private final JavaMailSender mailSender = mock(JavaMailSender.class);
	private final CorreoSmtp correo = new CorreoSmtp(mailSender, "no-responder@inversioneslr.com");

	@Test
	void armaElMensajeConElRemitenteConfigurado() {
		correo.enviar("huesped@correo.com", "Asunto", "Cuerpo");

		ArgumentCaptor<SimpleMailMessage> mensaje = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(mensaje.capture());
		assertThat(mensaje.getValue().getFrom()).isEqualTo("no-responder@inversioneslr.com");
		assertThat(mensaje.getValue().getTo()).containsExactly("huesped@correo.com");
		assertThat(mensaje.getValue().getSubject()).isEqualTo("Asunto");
		assertThat(mensaje.getValue().getText()).isEqualTo("Cuerpo");
	}

	@Test
	void unFalloDelServidorSeTraduceAlPuerto() {
		doThrow(new MailSendException("sin conexión")).when(mailSender).send(any(SimpleMailMessage.class));

		assertThatThrownBy(() -> correo.enviar("huesped@correo.com", "Asunto", "Cuerpo"))
				.isInstanceOf(CorreoNoEnviadoException.class);
	}
}
