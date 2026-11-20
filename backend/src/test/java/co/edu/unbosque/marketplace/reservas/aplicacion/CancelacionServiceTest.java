package co.edu.unbosque.marketplace.reservas.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.pagos.dominio.PasarelaNoDisponibleException;
import co.edu.unbosque.marketplace.pagos.dominio.PasarelaPagos;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

@ExtendWith(MockitoExtension.class)
class CancelacionServiceTest {

	private static final BigDecimal VALOR = new BigDecimal("346500.00");

	@Mock
	private GestionReservasService gestion;
	@Mock
	private ReservaTransacciones transacciones;
	@Mock
	private PasarelaPagos pasarela;

	private CancelacionService servicio;
	private Usuario huesped;
	private Reserva reserva;

	@BeforeEach
	void preparar() {
		servicio = new CancelacionService(gestion, transacciones, pasarela);
		huesped = TestDatos.usuario(2L, Rol.HUESPED, "hash", "huesped@correo.com");
		reserva = mock(Reserva.class);
	}

	private static ReservaDTO cancelada(Boolean devolucionPendiente) {
		return new ReservaDTO(50L, 10L, null, null, 2, null, null, 2L, null, null, null, null, null, null, null, null,
				null, null, null, "Motivo", null, VALOR, devolucionPendiente);
	}

	private void conDevolucionPendiente() {
		when(transacciones.reclamarDevolucion(50L)).thenReturn(OptionalInt.of(1));
		when(transacciones.buscar(50L)).thenReturn(Optional.of(reserva));
		when(reserva.getReferenciaPago()).thenReturn("pi_1");
		when(reserva.getValorDevolucion()).thenReturn(VALOR);
	}

	@Test
	void cancelaYPideLaDevolucionFueraDeLaTransaccion() {
		when(gestion.cancelar(huesped, 50L, "Motivo")).thenReturn(cancelada(true));
		conDevolucionPendiente();
		ReservaDTO devuelta = cancelada(false);
		when(transacciones.obtener(huesped, 50L)).thenReturn(devuelta);

		assertThat(servicio.cancelar(huesped, 50L, "Motivo")).isSameAs(devuelta);
		verify(pasarela).devolver("pi_1", VALOR, "reserva-50-cancelacion", 1);
		verify(transacciones).marcarDevolucionSolicitada(50L);
	}

	@Test
	void siLaPasarelaNoAceptaLaDevolucionLaCancelacionIgualQueda() {
		ReservaDTO pendiente = cancelada(true);
		when(gestion.cancelar(huesped, 50L, "Motivo")).thenReturn(pendiente);
		conDevolucionPendiente();
		doThrow(new PasarelaNoDisponibleException("caída")).when(pasarela).devolver("pi_1", VALOR,
				"reserva-50-cancelacion", 1);

		assertThat(servicio.cancelar(huesped, 50L, "Motivo")).isSameAs(pendiente);
		verify(transacciones, never()).marcarDevolucionSolicitada(any());
	}

	@Test
	void sinDevolucionPendienteNoLlamaALaPasarela() {
		ReservaDTO sinDevolucion = cancelada(false);
		when(gestion.cancelar(huesped, 50L, "Motivo")).thenReturn(sinDevolucion);

		assertThat(servicio.cancelar(huesped, 50L, "Motivo")).isSameAs(sinDevolucion);
		verify(pasarela, never()).devolver(any(), any(), any(), anyInt());
	}

	@Test
	void siOtraEjecucionYaLaReclamoOYaSeHizoNoLlamaALaPasarela() {
		when(transacciones.reclamarDevolucion(50L)).thenReturn(OptionalInt.empty());

		assertThat(servicio.devolver(50L)).isFalse();

		verify(pasarela, never()).devolver(any(), any(), any(), anyInt());
		verify(transacciones, never()).buscar(any());
	}

	@Test
	void devolverPideLaDevolucionReclamadaYLaMarca() {
		conDevolucionPendiente();

		assertThat(servicio.devolver(50L)).isTrue();

		verify(pasarela).devolver("pi_1", VALOR, "reserva-50-cancelacion", 1);
		verify(transacciones).marcarDevolucionSolicitada(50L);
	}

	@Test
	void elConciliadorReintentaLasPendientesYPropagaLaFalla() {
		when(transacciones.devolucionesPendientes()).thenReturn(List.of(50L));
		conDevolucionPendiente();
		doThrow(new PasarelaNoDisponibleException("caída")).when(pasarela).devolver("pi_1", VALOR,
				"reserva-50-cancelacion", 1);

		assertThat(servicio.devolucionesPendientes()).containsExactly(50L);
		assertThatThrownBy(() -> servicio.devolver(50L)).isInstanceOf(PasarelaNoDisponibleException.class);
	}
}
