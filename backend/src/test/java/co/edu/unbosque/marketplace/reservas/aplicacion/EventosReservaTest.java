package co.edu.unbosque.marketplace.reservas.aplicacion;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.cotizacion.dominio.CalculadoraCotizacion;
import co.edu.unbosque.marketplace.cotizacion.dominio.ConfiguracionCotizacion;
import co.edu.unbosque.marketplace.cotizacion.dominio.DescuentoEstadiaLarga;
import co.edu.unbosque.marketplace.disponibilidad.dominio.RangoFechas;
import co.edu.unbosque.marketplace.notificaciones.aplicacion.PublicadorEventos;
import co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

class EventosReservaTest {

	private final PublicadorEventos publicador = mock(PublicadorEventos.class);
	private final Auditoria auditoria = mock(Auditoria.class);
	private final EventosReserva eventos = new EventosReserva(publicador, auditoria);

	private Reserva reservaConfirmada() {
		Alojamiento alojamiento = TestDatos.alojamiento(10L, TestDatos.usuario(1L, Rol.ANFITRION, "hash"));
		Reserva reserva = new Reserva(TestDatos.usuario(2L, Rol.HUESPED, "hash", "huesped@correo.com"), alojamiento,
				new RangoFechas(LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 22)), 2,
				new CalculadoraCotizacion(List.of(new DescuentoEstadiaLarga())).calcular(alojamiento, 2, List.of(),
						new ConfiguracionCotizacion(BigDecimal.TEN, BigDecimal.TEN, 7)),
				null, AHORA);
		TestDatos.asignar(reserva, "id", 50L);
		reserva.confirmar("pi_1", AHORA);
		return reserva;
	}

	@Test
	void cadaCambioQuedaEnElOutboxYEnLaAuditoriaConSusEstados() {
		Reserva reserva = reservaConfirmada();

		eventos.publicar("RESERVA_CONFIRMADA", reserva, EstadoReserva.PENDIENTE_PAGO);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
		verify(publicador).publicar(eq("RESERVA_CONFIRMADA"), eq("RESERVA"), eq(50L), datos.capture());
		assertThat(datos.getValue()).containsEntry("huespedId", 2L).containsEntry("anfitrionId", 1L)
				.containsEntry("estado", EstadoReserva.CONFIRMADA);
		verify(auditoria).registrar(EventoAuditoria.cambio("RESERVA_CONFIRMADA", "RESERVA", 50L,
				EstadoReserva.PENDIENTE_PAGO, EstadoReserva.CONFIRMADA, null));
	}

	@Test
	void elMotivoVaAlDetalleDeLaAuditoria() {
		Reserva reserva = reservaConfirmada();
		reserva.ponerEnRevision(AHORA);

		eventos.publicar("RESERVA_EN_REVISION", reserva, EstadoReserva.CONFIRMADA, Map.of("motivo", "Bloqueo"));

		verify(publicador).publicar(eq("RESERVA_EN_REVISION"), eq("RESERVA"), eq(50L), anyMap());
		verify(auditoria).registrar(EventoAuditoria.cambio("RESERVA_EN_REVISION", "RESERVA", 50L,
				EstadoReserva.CONFIRMADA, EstadoReserva.EN_REVISION, "Bloqueo"));
	}
}
