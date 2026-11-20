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
	void laReferenciaDelCobroSeGuardaUnaSolaVezMientrasEstaPendiente() {
		reserva.registrarReferencia("pi_1", AHORA);
		reserva.registrarReferencia("pi_2", AHORA);
		assertThat(reserva.getReferenciaPago()).isEqualTo("pi_1");

		reserva.marcarPagoFallido("Sin respuesta", null, true, AHORA);
		assertThat(reserva.getReferenciaPago()).isEqualTo("pi_1");

		reserva.registrarReferencia("pi_3", AHORA);
		assertThat(reserva.getReferenciaPago()).isEqualTo("pi_1");
	}

	@Test
	void cadaIntentoDeDevolucionTieneUnNumeroNuevo() {
		assertThat(reserva.getIntentosDevolucion()).isZero();

		assertThat(reserva.siguienteIntentoDevolucion(AHORA)).isEqualTo(1);
		assertThat(reserva.siguienteIntentoDevolucion(AHORA.plusSeconds(5))).isEqualTo(2);
		assertThat(reserva.getActualizadoEn()).isEqualTo(AHORA.plusSeconds(5));
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

	// ─────────────────────────────────────────
	// Cancelar y completar — MA-71, MA-72
	// ─────────────────────────────────────────

	private static final LocalDate ANTES_DE_LLEGAR = RANGO.llegada().minusDays(1);

	@Test
	void elHuespedCancelaUnaReservaConfirmadaAntesDeLlegar() {
		reserva.confirmar("pi_1", AHORA);

		reserva.cancelar(2L, "  Cambio de planes ", ANTES_DE_LLEGAR, AHORA.plusSeconds(60));

		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
		assertThat(reserva.getMotivoCancelacion()).isEqualTo("Cambio de planes");
		assertThat(reserva.getCanceladaPor()).isEqualTo(2L);
		assertThat(reserva.getCanceladaEn()).isEqualTo(AHORA.plusSeconds(60));
	}

	@Test
	void unaReservaEnRevisionTambienSePuedeCancelar() {
		reserva.confirmar("pi_1", AHORA);
		TestDatos.asignar(reserva, "estado", EstadoReserva.EN_REVISION);

		reserva.cancelar(2L, "No puedo viajar", ANTES_DE_LLEGAR, AHORA);

		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
	}

	@Test
	void cancelarSinMotivoSeRechaza() {
		reserva.confirmar("pi_1", AHORA);

		assertThatThrownBy(() -> reserva.cancelar(2L, "  ", ANTES_DE_LLEGAR, AHORA))
				.hasFieldOrPropertyWithValue("codigo", "MOTIVO_REQUERIDO");
		assertThatThrownBy(() -> reserva.cancelar(2L, null, ANTES_DE_LLEGAR, AHORA))
				.hasFieldOrPropertyWithValue("codigo", "MOTIVO_REQUERIDO");
		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
	}

	@Test
	void unaReservaEnCursoNoSePuedeCancelar() {
		reserva.confirmar("pi_1", AHORA);
		LocalDate diaDeLlegada = RANGO.llegada();

		assertThatThrownBy(() -> reserva.cancelar(2L, "Motivo", diaDeLlegada, AHORA))
				.isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", "CANCELACION_NO_PERMITIDA");
		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
	}

	@ParameterizedTest
	@EnumSource(value = EstadoReserva.class, names = { "PENDIENTE_PAGO", "PAGO_FALLIDO", "CANCELADA", "COMPLETADA" })
	void soloSeCancelaUnaReservaConfirmadaOEnRevision(EstadoReserva estado) {
		TestDatos.asignar(reserva, "estado", estado);

		assertThatThrownBy(() -> reserva.cancelar(2L, "Motivo", ANTES_DE_LLEGAR, AHORA))
				.isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", "CANCELACION_NO_PERMITIDA");
	}

	@Test
	void seCompletaCuandoLlegaLaFechaDeSalida() {
		reserva.confirmar("pi_1", AHORA);

		assertThat(reserva.completar(RANGO.salida().minusDays(1), AHORA)).isFalse();
		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
		assertThat(reserva.completar(RANGO.salida(), AHORA)).isTrue();
		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.COMPLETADA);
	}

	@Test
	void soloSeCompletaUnaReservaConfirmada() {
		assertThat(reserva.completar(RANGO.salida().plusDays(1), AHORA)).isFalse();
		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
	}

	@ParameterizedTest
	@EnumSource(EstadoReserva.class)
	void fueConfirmadaSiPasoPorCONFIRMADA(EstadoReserva estado) {
		boolean esperado = estado != EstadoReserva.PENDIENTE_PAGO && estado != EstadoReserva.PAGO_FALLIDO;

		assertThat(estado.fueConfirmada()).isEqualTo(esperado);
	}

	@Test
	void sabeQuienHizoLaReserva() {
		assertThat(reserva.esDe(2L)).isTrue();
		assertThat(reserva.esDe(1L)).isFalse();
	}
}
