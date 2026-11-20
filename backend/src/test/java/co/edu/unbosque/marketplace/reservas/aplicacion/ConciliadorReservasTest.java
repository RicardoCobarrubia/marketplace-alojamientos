package co.edu.unbosque.marketplace.reservas.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.unbosque.marketplace.pagos.dominio.PasarelaNoDisponibleException;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;

@ExtendWith(MockitoExtension.class)
class ConciliadorReservasTest {

	@Mock
	private ReservaTransacciones transacciones;
	@Mock
	private ReservaService reservaService;
	@Mock
	private GestionReservasService gestion;
	@Mock
	private CancelacionService cancelaciones;

	@Test
	void venceLasRetencionesYVerificaLosPagosEnDuda() {
		Reserva vencida = mock(Reserva.class);
		Reserva enDuda = mock(Reserva.class);
		when(transacciones.pendientesVencidas()).thenReturn(List.of(1L));
		when(transacciones.pagosPorVerificar()).thenReturn(List.of(2L));
		when(transacciones.buscar(1L)).thenReturn(Optional.of(vencida));
		when(transacciones.buscar(2L)).thenReturn(Optional.of(enDuda));

		new ConciliadorReservas(transacciones, reservaService, gestion, cancelaciones).conciliar();

		verify(reservaService).vencer(vencida);
		verify(reservaService).verificarPago(enDuda);
	}

	@Test
	void unaFallaNoDetieneLasDemas() {
		Reserva primera = mock(Reserva.class);
		Reserva segunda = mock(Reserva.class);
		when(transacciones.pendientesVencidas()).thenReturn(List.of(1L, 2L));
		when(transacciones.buscar(1L)).thenReturn(Optional.of(primera));
		when(transacciones.buscar(2L)).thenReturn(Optional.of(segunda));
		doThrow(new IllegalStateException("falla")).when(reservaService).vencer(primera);
		when(transacciones.pagosPorVerificar()).thenReturn(List.of(3L));
		Reserva enDuda = mock(Reserva.class);
		when(transacciones.buscar(3L)).thenReturn(Optional.of(enDuda));
		doThrow(new PasarelaNoDisponibleException("caída")).when(reservaService).verificarPago(enDuda);
		ConciliadorReservas conciliador = new ConciliadorReservas(transacciones, reservaService, gestion, cancelaciones);

		assertThat(conciliador.vencerRetenciones()).isEqualTo(1);
		assertThat(conciliador.verificarPagosEnDuda()).isZero();
		verify(reservaService).vencer(segunda);
	}

	@Test
	void completaLasReservasTerminadasAunqueUnaFalle() {
		when(gestion.porCompletar()).thenReturn(List.of(1L, 2L, 3L));
		when(gestion.completar(1L)).thenReturn(true);
		when(gestion.completar(2L)).thenThrow(new IllegalStateException("falla"));
		when(gestion.completar(3L)).thenReturn(false);

		assertThat(new ConciliadorReservas(transacciones, reservaService, gestion, cancelaciones).completarTerminadas()).isEqualTo(1);
		verify(gestion).completar(3L);
	}

	@Test
	void reintentaLasDevolucionesPendientesAunqueUnaFalle() {
		when(cancelaciones.devolucionesPendientes()).thenReturn(List.of(1L, 2L, 3L));
		doThrow(new PasarelaNoDisponibleException("caída")).when(cancelaciones).devolver(1L);
		doThrow(new IllegalStateException("falla")).when(cancelaciones).devolver(2L);

		assertThat(new ConciliadorReservas(transacciones, reservaService, gestion, cancelaciones)
				.reintentarDevoluciones()).isEqualTo(1);
		verify(cancelaciones).devolver(3L);
	}
}
