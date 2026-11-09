package co.edu.unbosque.marketplace.reservas.aplicacion;

/**
 * Respuesta HTTP de una solicitud de reserva, ya serializada. Se guarda tal cual en la clave de
 * idempotencia para que un reintento reciba exactamente la misma respuesta (ADR-005).
 */
public record RespuestaReserva(int codigoHttp, String cuerpo) {

	public boolean esError() {
		return codigoHttp >= 400;
	}
}
