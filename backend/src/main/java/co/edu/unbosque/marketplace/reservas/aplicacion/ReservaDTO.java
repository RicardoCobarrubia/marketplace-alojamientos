package co.edu.unbosque.marketplace.reservas.aplicacion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import co.edu.unbosque.marketplace.alojamientos.dominio.ServicioAdicional.Modalidad;
import co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;
import co.edu.unbosque.marketplace.reservas.dominio.ReservaServicio;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * DTO de reserva: se usa para solicitarla (MA-66) y para responder.
 * <p>
 * El huésped solo envía el alojamiento, las fechas, el número de huéspedes y los servicios. Los
 * valores los calcula el sistema con la cotización: si el cliente los envía se ignoran.
 */
public record ReservaDTO(

		@JsonProperty(access = Access.READ_ONLY)
		Long id,

		@NotNull(message = "El alojamiento es obligatorio.")
		Long alojamientoId,

		@NotNull(message = "La fecha de llegada es obligatoria.")
		LocalDate llegada,

		@NotNull(message = "La fecha de salida es obligatoria.")
		LocalDate salida,

		@NotNull(message = "El número de huéspedes es obligatorio.")
		@Positive(message = "El número de huéspedes debe ser mayor que cero.")
		Integer huespedes,

		Set<@NotNull(message = "Un servicio adicional está vacío.") Long> servicioIds,

		@JsonProperty(access = Access.READ_ONLY)
		EstadoReserva estado,

		@JsonProperty(access = Access.READ_ONLY)
		Long huespedId,

		@JsonProperty(access = Access.READ_ONLY)
		Integer noches,

		@JsonProperty(access = Access.READ_ONLY)
		BigDecimal precioNoche,

		@JsonProperty(access = Access.READ_ONLY)
		BigDecimal subtotal,

		@JsonProperty(access = Access.READ_ONLY)
		BigDecimal totalDescuentos,

		@JsonProperty(access = Access.READ_ONLY)
		BigDecimal limpieza,

		@JsonProperty(access = Access.READ_ONLY)
		List<ServicioReservado> servicios,

		@JsonProperty(access = Access.READ_ONLY)
		BigDecimal totalServicios,

		@JsonProperty(access = Access.READ_ONLY)
		BigDecimal tarifaServicio,

		@JsonProperty(access = Access.READ_ONLY)
		BigDecimal total,

		@JsonProperty(access = Access.READ_ONLY)
		String motivoFallo,

		@JsonProperty(access = Access.READ_ONLY)
		Instant creadoEn,

		@JsonProperty(access = Access.READ_ONLY)
		String motivoCancelacion,

		@JsonProperty(access = Access.READ_ONLY)
		Instant canceladaEn) {

	/** Servicio adicional contratado con el precio que tenía al reservar. */
	public record ServicioReservado(Long servicioId, String nombre, Modalidad modalidad, BigDecimal costoUnitario,
			int cantidad, BigDecimal valor) {

		static ServicioReservado desde(ReservaServicio servicio) {
			return new ServicioReservado(servicio.getServicioAdicionalId(), servicio.getNombre(),
					servicio.getModalidad(), servicio.getCostoUnitario(), servicio.getCantidad(), servicio.getValor());
		}
	}

	public static ReservaDTO desde(Reserva reserva) {
		List<ServicioReservado> servicios = reserva.getServicios().stream().map(ServicioReservado::desde).toList();
		return new ReservaDTO(reserva.getId(), reserva.getAlojamiento().getId(), reserva.getFechaLlegada(),
				reserva.getFechaSalida(), reserva.getHuespedes(),
				Set.copyOf(servicios.stream().map(ServicioReservado::servicioId).toList()), reserva.getEstado(),
				reserva.getHuesped().getId(), reserva.getNoches(), reserva.getPrecioNoche(), reserva.getSubtotal(),
				reserva.getTotalDescuentos(), reserva.getLimpieza(), servicios, reserva.getTotalServicios(),
				reserva.getTarifaServicio(), reserva.getTotal(), reserva.getMotivoFallo(), reserva.getCreadoEn(),
				reserva.getMotivoCancelacion(), reserva.getCanceladaEn());
	}
}
