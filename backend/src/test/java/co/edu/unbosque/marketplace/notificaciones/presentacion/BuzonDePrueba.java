package co.edu.unbosque.marketplace.notificaciones.presentacion;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import co.edu.unbosque.marketplace.notificaciones.dominio.CorreoNoEnviadoException;
import co.edu.unbosque.marketplace.notificaciones.dominio.EnvioCorreo;

/** Guarda en memoria los correos enviados y permite simular un servidor caído. */
public class BuzonDePrueba implements EnvioCorreo {

	/** Un correo recibido. */
	public record Correo(String destinatario, String asunto, String cuerpo) {
	}

	private final List<Correo> recibidos = new CopyOnWriteArrayList<>();
	private volatile boolean caido;

	public void reiniciar() {
		recibidos.clear();
		caido = false;
	}

	public void caido(boolean caido) {
		this.caido = caido;
	}

	public List<Correo> recibidos() {
		return List.copyOf(recibidos);
	}

	@Override
	public void enviar(String destinatario, String asunto, String cuerpo) {
		if (caido) {
			throw new CorreoNoEnviadoException("Servidor de correo caído (simulado)", null);
		}
		recibidos.add(new Correo(destinatario, asunto, cuerpo));
	}
}
