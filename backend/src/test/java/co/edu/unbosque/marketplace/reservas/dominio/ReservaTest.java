package co.edu.unbosque.marketplace.reservas.dominio;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.ServicioAdicional;
import co.edu.unbosque.marketplace.alojamientos.dominio.ServicioAdicional.Modalidad;
import co.edu.unbosque.marketplace.comun.excepcion.ReglaNegocioException;
import co.edu.unbosque.marketplace.cotizacion.dominio.CalculadoraCotizacion;
import co.edu.unbosque.marketplace.cotizacion.dominio.ConfiguracionCotizacion;
import co.edu.unbosque.marketplace.cotizacion.dominio.Cotizacion;
import co.edu.unbosque.marketplace.cotizacion.dominio.DescuentoEstadiaLarga;
import co.edu.unbosque.marketplace.disponibilidad.dominio.RangoFechas;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

class ReservaTest {

	private static final RangoFechas RANGO = new RangoFechas(LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 23));

	private Usuario anfitrion;
	private Usuario huesped;
	private Reserva reserva;

	@BeforeEach
	void preparar() {
		anfitrion = TestDatos.usuario(1L, Rol.ANFITRION, "hash");
		huesped = TestDatos.usuario(2L, Rol.HUESPED, "hash", "huesped@correo.com");
		Alojamiento alojamiento = TestDatos.alojamiento(10L, anfitrion);
		ServicioAdicional desayuno = alojamiento.agregarServicioAdicional("Desayuno", new BigDecimal("30000"),
				Modalidad.POR_NOCHE, AHORA);
		TestDatos.asignar(desayuno, "id", 4L);
		Cotizacion cotizacion = new CalculadoraCotizacion(List.of(new DescuentoEstadiaLarga())).calcular(alojamiento,
				RANGO.noches(), List.of(desayuno),
				new ConfiguracionCotizacion(new BigDecimal("10"), new BigDecimal("10"), 7));
		reserva = new Reserva(huesped, alojamiento, RANGO, 2, cotizacion, "peticion-1", AHORA);
	}

	@Test
	void naceEnPendientePagoConLaCopiaDeLaCotizacionYLaPolitica() {
		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
		assertThat(reserva.getNoches()).isEqualTo(3);
		assertThat(reserva.getSubtotal()).isEqualByComparingTo("750000");
		assertThat(reserva.getServicios()).singleElement()
				.satisfies(servicio -> assertThat(servicio.getValor()).isEqualByComparingTo("90000"));
		assertThat(reserva.getTotal()).isEqualByComparingTo("968000");
		assertThat(reserva.getPoliticaCancelacion()).isEqualTo("{}");
		assertThat(reserva.getCorrelationId()).isEqualTo("peticion-1");
		assertThat(reserva.rango()).isEqualTo(RANGO);
	}

	@Test
	void seConfirmaConLaReferenciaDelPago() {
		reserva.confirmar("pi_123", AHORA.plusSeconds(3));

		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
		assertThat(reserva.getReferenciaPago()).isEqualTo("pi_123");
		assertThat(reserva.getActualizadoEn()).isEqualTo(AHORA.plusSeconds(3));
	}

	@Test
	void unPagoFallidoNoSePuedeConfirmarDespues() {
		reserva.marcarPagoFallido("Sin fondos", null, true, AHORA);

		assertThat(reserva.getMotivoFallo()).isEqualTo("Sin fondos");
		assertThat(reserva.isPagoPorVerificar()).isTrue();
		assertThatThrownBy(() -> reserva.confirmar("pi_123", AHORA))
				.isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", "TRANSICION_INVALIDA");

		reserva.pagoVerificado("pi_9", AHORA);
		assertThat(reserva.isPagoPorVerificar()).isFalse();
		assertThat(reserva.getReferenciaPago()).isEqualTo("pi_9");
		reserva.marcarPagoPorVerificar(AHORA);
		assertThat(reserva.isPagoPorVerificar()).isTrue();
	}

	@Test
	void laVenElHuespedElAnfitrionYElAdministrador() {
		Usuario otro = TestDatos.usuario(3L, Rol.HUESPED, "hash", "otro@correo.com");
		Usuario administrador = TestDatos.usuario(9L, Rol.ADMINISTRADOR, "hash", "admin@correo.com");

		assertThat(reserva.esVisiblePara(huesped)).isTrue();
		assertThat(reserva.esVisiblePara(anfitrion)).isTrue();
		assertThat(reserva.esVisiblePara(administrador)).isTrue();
		assertThat(reserva.esVisiblePara(otro)).isFalse();
	}

	@Test
	void transicionesPermitidasSegunElRequisito() {
		assertThat(EstadoReserva.PENDIENTE_PAGO.siguientes())
				.containsExactlyInAnyOrder(EstadoReserva.CONFIRMADA, EstadoReserva.PAGO_FALLIDO);
		assertThat(EstadoReserva.CONFIRMADA.siguientes())
				.containsExactlyInAnyOrder(EstadoReserva.EN_REVISION, EstadoReserva.CANCELADA, EstadoReserva.COMPLETADA);
		assertThat(EstadoReserva.EN_REVISION.siguientes())
				.containsExactlyInAnyOrder(EstadoReserva.CONFIRMADA, EstadoReserva.CANCELADA);
		assertThat(EstadoReserva.CONFIRMADA.puedePasarA(EstadoReserva.PENDIENTE_PAGO)).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = EstadoReserva.class, names = { "PAGO_FALLIDO", "CANCELADA", "COMPLETADA" })
	void losEstadosFinalesNoTienenSalida(EstadoReserva estado) {
		assertThat(estado.siguientes()).isEmpty();
	}
}
