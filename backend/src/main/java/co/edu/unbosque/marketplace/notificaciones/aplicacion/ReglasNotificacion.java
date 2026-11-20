package co.edu.unbosque.marketplace.notificaciones.aplicacion;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

/**
 * Quién recibe qué por cada tipo de evento (MA-75). Los datos salen del propio evento, así este
 * módulo no depende de reservas ni de alojamientos. Un tipo sin regla no notifica a nadie.
 * <ul>
 * <li>Reserva confirmada, cancelada, mantenida o en revisión: huésped y anfitrión; en revisión,
 * también los administradores (escenario 1).</li>
 * <li>Pago fallido y devolución solicitada: el huésped (escenario 1).</li>
 * <li>Alojamiento aprobado, rechazado o bloqueado: el anfitrión (escenario 2).</li>
 * </ul>
 */
@Component
public class ReglasNotificacion {

	private static final String HUESPED = "huespedId";
	private static final String ANFITRION = "anfitrionId";
	private static final String RESERVA = "reservaId";
	private static final String MOTIVO = "motivo";
	private static final String TITULO_ALOJAMIENTO = "titulo";
	private static final String LLEGADA = "llegada";
	private static final String SALIDA = "salida";

	/**
	 * @param administradores ids de los administradores activos; solo se piden si el evento los
	 *        necesita
	 */
	public List<Aviso> avisos(String tipo, JsonNode datos, Supplier<List<Long>> administradores) {
		List<Aviso> avisos = new ArrayList<>();
		switch (tipo) {
			case "RESERVA_CONFIRMADA" -> {
				avisos.add(new Aviso(id(datos, HUESPED), "Reserva confirmada",
						"Tu reserva #%d del %s al %s quedó confirmada.".formatted(id(datos, RESERVA), texto(datos, LLEGADA), texto(datos, SALIDA))));
				avisos.add(new Aviso(id(datos, ANFITRION), "Nueva reserva",
						"Tienes una reserva confirmada (#%d) del %s al %s.".formatted(id(datos, RESERVA), texto(datos, LLEGADA), texto(datos, SALIDA))));
			}
			case "RESERVA_CANCELADA" -> {
				avisos.add(new Aviso(id(datos, HUESPED), "Reserva cancelada",
						"La reserva #%d fue cancelada. Se te devolverán %s.".formatted(id(datos, RESERVA),
								pesos(datos.path("valorDevolucion").decimalValue()))));
				avisos.add(new Aviso(id(datos, ANFITRION), "Reserva cancelada",
						"La reserva #%d del %s al %s fue cancelada y las fechas quedaron libres."
								.formatted(id(datos, RESERVA), texto(datos, LLEGADA), texto(datos, SALIDA))));
			}
			case "RESERVA_EN_REVISION" -> {
				String mensaje = "La reserva #%d quedó en revisión por moderación: %s."
						.formatted(id(datos, RESERVA), texto(datos, MOTIVO));
				avisos.add(new Aviso(id(datos, HUESPED), "Reserva en revisión", mensaje));
				avisos.add(new Aviso(id(datos, ANFITRION), "Reserva en revisión", mensaje));
				administradores.get().forEach(admin -> avisos.add(new Aviso(admin, "Reserva por revisar", mensaje)));
			}
			case "RESERVA_MANTENIDA" -> {
				String mensaje = "La reserva #%d terminó su revisión y sigue confirmada.".formatted(id(datos, RESERVA));
				avisos.add(new Aviso(id(datos, HUESPED), "Reserva confirmada de nuevo", mensaje));
				avisos.add(new Aviso(id(datos, ANFITRION), "Reserva confirmada de nuevo", mensaje));
			}
			case "PAGO_FALLIDO" -> avisos.add(new Aviso(id(datos, HUESPED), "Pago no completado",
					"El pago de la reserva #%d no se completó y las fechas quedaron libres.".formatted(id(datos, RESERVA))));
			case "DEVOLUCION_SOLICITADA" -> avisos.add(new Aviso(id(datos, HUESPED), "Devolución en proceso",
					"Solicitamos la devolución de %s de la reserva #%d.".formatted(
							pesos(datos.path("valorDevolucion").decimalValue()), id(datos, RESERVA))));
			case "ALOJAMIENTO_APROBADO" -> avisos.add(new Aviso(id(datos, ANFITRION), "Alojamiento aprobado",
					"«%s» fue aprobado y ya está publicado.".formatted(texto(datos, TITULO_ALOJAMIENTO))));
			case "ALOJAMIENTO_RECHAZADO" -> avisos.add(new Aviso(id(datos, ANFITRION), "Alojamiento rechazado",
					"«%s» no fue aprobado: %s.".formatted(texto(datos, TITULO_ALOJAMIENTO), texto(datos, MOTIVO))));
			case "ALOJAMIENTO_BLOQUEADO" -> avisos.add(new Aviso(id(datos, ANFITRION), "Alojamiento bloqueado",
					"«%s» fue bloqueado: %s.".formatted(texto(datos, TITULO_ALOJAMIENTO), texto(datos, MOTIVO))));
			default -> {
				// Sin regla: el evento se marca entregado sin notificar a nadie
			}
		}
		return avisos;
	}

	private static long id(JsonNode datos, String campo) {
		JsonNode valor = datos.path(campo);
		if (!valor.isNumber()) {
			throw new IllegalArgumentException("El evento no tiene el campo numérico " + campo + ".");
		}
		return valor.longValue();
	}

	private static String texto(JsonNode datos, String campo) {
		return datos.path(campo).asString("").strip();
	}

	/** Pesos colombianos sin decimales: $693.000. */
	static String pesos(BigDecimal valor) {
		DecimalFormat formato = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.of("es", "CO")));
		return "$" + formato.format(valor);
	}
}
