package co.edu.unbosque.marketplace.reservas.aplicacion;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.comun.excepcion.ReglaNegocioException;
import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.cotizacion.dominio.CalculadoraCotizacion;
import co.edu.unbosque.marketplace.cotizacion.dominio.ConfiguracionCotizacion;
import co.edu.unbosque.marketplace.cotizacion.dominio.DescuentoEstadiaLarga;
import co.edu.unbosque.marketplace.disponibilidad.dominio.RangoFechas;
import co.edu.unbosque.marketplace.pagos.dominio.PasarelaNoDisponibleException;
import co.edu.unbosque.marketplace.pagos.dominio.PasarelaPagos;
import co.edu.unbosque.marketplace.pagos.dominio.ResultadoPago;
import co.edu.unbosque.marketplace.pagos.dominio.SolicitudCobro;
import co.edu.unbosque.marketplace.reservas.aplicacion.ReservaTransacciones.Desenlace;
import co.edu.unbosque.marketplace.reservas.dominio.ClaveIdempotencia;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

	private static final String CLAVE = "intento-1";
	private static final String CLAVE_PAGO = "reserva-50";
	private static final RespuestaReserva CREADA = new RespuestaReserva(201, "{\"id\":50}");
	private static final RespuestaReserva FALLIDA = new RespuestaReserva(402, "{\"codigo\":\"X\"}");

	@Mock
	private ReservaTransacciones transacciones;
	@Mock
	private PasarelaPagos pasarela;

	private ReservaService servicio;
	private Usuario huesped;
	private Reserva reserva;
	private ReservaDTO solicitud;

	@BeforeEach
	void preparar() {
		servicio = new ReservaService(transacciones, pasarela, Clock.fixed(AHORA, ZoneOffset.UTC),
				Duration.ofSeconds(10));
		huesped = TestDatos.usuario(2L, Rol.HUESPED, "hash", "huesped@correo.com");
		Alojamiento alojamiento = TestDatos.alojamiento(10L, TestDatos.usuario(1L, Rol.ANFITRION, "hash"));
		RangoFechas rango = new RangoFechas(LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 22));
		reserva = new Reserva(huesped, alojamiento, rango, 2,
				new CalculadoraCotizacion(List.of(new DescuentoEstadiaLarga())).calcular(alojamiento, 2, List.of(),
						new ConfiguracionCotizacion(BigDecimal.TEN, BigDecimal.TEN, 7)),
				null, AHORA);
		TestDatos.asignar(reserva, "id", 50L);
		solicitud = new ReservaDTO(null, 10L, rango.llegada(), rango.salida(), 2, Set.of(), null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null);
	}

	private void seCrea() {
		when(transacciones.iniciar(huesped, CLAVE, ReservaService.huella(solicitud), solicitud)).thenReturn(reserva);
	}

	private ClaveIdempotencia clavePrevia(String huella) {
		ClaveIdempotencia clave = new ClaveIdempotencia(2L, CLAVE, huella, AHORA);
		clave.asociar(50L);
		return clave;
	}

	// ─────────────────────────────────────────
	// Flujo normal
	// ─────────────────────────────────────────

	private void sePrepara() {
		seCrea();
		when(pasarela.preparar(any(SolicitudCobro.class))).thenReturn("pi_1");
	}

	@Test
	void exigeLaClaveDeIdempotencia() {
		assertThatThrownBy(() -> servicio.reservar(huesped, " ", solicitud))
				.isInstanceOf(SolicitudInvalidaException.class)
				.hasFieldOrPropertyWithValue("codigo", "CLAVE_IDEMPOTENCIA_REQUERIDA");
		String larga = "x".repeat(101);
		assertThatThrownBy(() -> servicio.reservar(huesped, larga, solicitud))
				.isInstanceOf(SolicitudInvalidaException.class);
	}

	@Test
	void guardaLaReferenciaAntesDeCobrarYConfirma() {
		sePrepara();
		SolicitudCobro cobro = new SolicitudCobro(CLAVE_PAGO, reserva.getTotal(), "Reserva 50");
		when(pasarela.cobrar("pi_1", cobro)).thenReturn(ResultadoPago.aprobado("pi_1"));
		when(transacciones.confirmar(50L, "pi_1")).thenReturn(new Desenlace(CREADA, false));

		assertThat(servicio.reservar(huesped, CLAVE, solicitud)).isEqualTo(CREADA);

		var orden = inOrder(pasarela, transacciones);
		orden.verify(pasarela).preparar(cobro);
		orden.verify(transacciones).registrarReferencia(50L, "pi_1");
		orden.verify(pasarela).cobrar("pi_1", cobro);
		verify(pasarela, never()).devolver(any(), any(), any(), anyInt());
	}

	@Test
	void siLasFechasSeLiberaronMientrasPagabaDevuelveElCobro() {
		sePrepara();
		when(pasarela.cobrar(eq("pi_1"), any())).thenReturn(ResultadoPago.aprobado("pi_1"));
		when(transacciones.confirmar(50L, "pi_1")).thenReturn(new Desenlace(FALLIDA, true));
		when(transacciones.siguienteIntentoDevolucion(50L)).thenReturn(1);

		assertThat(servicio.reservar(huesped, CLAVE, solicitud)).isEqualTo(FALLIDA);
		verify(pasarela).devolver("pi_1", reserva.getTotal(), CLAVE_PAGO, 1);
		verify(transacciones).marcarPagoVerificado(50L, "pi_1");
	}

	@Test
	void conElPagoRechazadoCompensaCon402() {
		sePrepara();
		when(pasarela.cobrar(eq("pi_1"), any())).thenReturn(ResultadoPago.rechazado("pi_1", "Sin fondos"));
		when(transacciones.compensar(eq(50L), eq(HttpStatus.PAYMENT_REQUIRED), eq("PAGO_RECHAZADO"), any(),
				eq("pi_1"), eq(false), eq(false))).thenReturn(new Desenlace(FALLIDA, false));

		assertThat(servicio.reservar(huesped, CLAVE, solicitud)).isEqualTo(FALLIDA);
	}

	// ─────────────────────────────────────────
	// Pasarela sin respuesta — MA-68
	// ─────────────────────────────────────────

	@Test
	void siNoRespondeAlPrepararNoHuboCobroYSeCompensaSinDudas() {
		seCrea();
		when(pasarela.preparar(any())).thenThrow(new PasarelaNoDisponibleException("timeout"));
		when(transacciones.compensar(eq(50L), eq(HttpStatus.GATEWAY_TIMEOUT), eq("PASARELA_SIN_RESPUESTA"), any(),
				isNull(), eq(false), eq(false))).thenReturn(new Desenlace(FALLIDA, false));

		assertThat(servicio.reservar(huesped, CLAVE, solicitud)).isEqualTo(FALLIDA);
		verify(transacciones, never()).registrarReferencia(any(), any());
		verify(pasarela, never()).cobrar(any(), any());
	}

	@Test
	void siNoRespondeAlCobrarPeroSiCobroCompensaYDevuelve() {
		sePrepara();
		when(pasarela.cobrar(eq("pi_1"), any())).thenThrow(new PasarelaNoDisponibleException("timeout"));
		when(pasarela.consultar("pi_1")).thenReturn(ResultadoPago.aprobado("pi_1"));
		when(transacciones.compensar(eq(50L), eq(HttpStatus.GATEWAY_TIMEOUT), eq("PASARELA_SIN_RESPUESTA"), any(),
				isNull(), eq(false), eq(true))).thenReturn(new Desenlace(FALLIDA, true));
		when(transacciones.siguienteIntentoDevolucion(50L)).thenReturn(1);

		servicio.reservar(huesped, CLAVE, solicitud);

		verify(pasarela).devolver("pi_1", reserva.getTotal(), CLAVE_PAGO, 1);
	}

	@Test
	void siNoRespondeNiCobroQuedaPorVerificarUnaVezMas() {
		sePrepara();
		when(pasarela.cobrar(eq("pi_1"), any())).thenThrow(new PasarelaNoDisponibleException("timeout"));
		when(pasarela.consultar("pi_1")).thenReturn(ResultadoPago.noCobrado());
		when(transacciones.compensar(eq(50L), eq(HttpStatus.GATEWAY_TIMEOUT), any(), any(), isNull(), eq(true),
				eq(false))).thenReturn(new Desenlace(FALLIDA, false));

		servicio.reservar(huesped, CLAVE, solicitud);

		verify(pasarela, never()).devolver(any(), any(), any(), anyInt());
	}

	@Test
	void siTampocoRespondeLaConsultaQuedaPorVerificar() {
		sePrepara();
		when(pasarela.cobrar(eq("pi_1"), any())).thenThrow(new PasarelaNoDisponibleException("timeout"));
		when(pasarela.consultar("pi_1")).thenThrow(new PasarelaNoDisponibleException("caída"));
		when(transacciones.compensar(eq(50L), eq(HttpStatus.GATEWAY_TIMEOUT), any(), any(), isNull(), eq(true),
				eq(false))).thenReturn(new Desenlace(FALLIDA, false));

		assertThat(servicio.reservar(huesped, CLAVE, solicitud)).isEqualTo(FALLIDA);
	}

	@Test
	void siLaDevolucionFallaElConciliadorLaReintenta() {
		sePrepara();
		when(pasarela.cobrar(eq("pi_1"), any())).thenReturn(ResultadoPago.aprobado("pi_1"));
		when(transacciones.confirmar(50L, "pi_1")).thenReturn(new Desenlace(FALLIDA, true));
		when(transacciones.siguienteIntentoDevolucion(50L)).thenReturn(1);
		doThrow(new PasarelaNoDisponibleException("caída")).when(pasarela).devolver("pi_1", reserva.getTotal(),
				CLAVE_PAGO, 1);

		servicio.reservar(huesped, CLAVE, solicitud);

		verify(transacciones).marcarPagoPorVerificar(50L);
		verify(transacciones, never()).marcarPagoVerificado(any(), any());
	}

	// ─────────────────────────────────────────
	// Reintentos y concurrencia — MA-69, ADR-003
	// ─────────────────────────────────────────

	@Test
	void lasFechasOcupadasSeRechazanCon409() {
		when(transacciones.iniciar(any(), any(), any(), any())).thenThrow(new DataIntegrityViolationException("ex"));
		when(transacciones.buscarClave(2L, CLAVE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.reservar(huesped, CLAVE, solicitud))
				.isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", "FECHAS_NO_DISPONIBLES");
	}

	@Test
	void unInterbloqueoEntreReservasSimultaneasEsUnConflictoControlado() {
		when(transacciones.iniciar(any(), any(), any(), any()))
				.thenThrow(new CannotAcquireLockException("deadlock detected"));
		when(transacciones.buscarClave(2L, CLAVE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.reservar(huesped, CLAVE, solicitud))
				.isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", "FECHAS_NO_DISPONIBLES");
	}

	@Test
	void unReintentoTerminadoRecibeLaMismaRespuestaSinCobrar() {
		ClaveIdempotencia previa = clavePrevia(ReservaService.huella(solicitud));
		previa.terminar(201, "{\"id\":50}", AHORA);
		when(transacciones.iniciar(any(), any(), any(), any())).thenThrow(new DataIntegrityViolationException("uq"));
		when(transacciones.buscarClave(2L, CLAVE)).thenReturn(Optional.of(previa));

		assertThat(servicio.reservar(huesped, CLAVE, solicitud)).isEqualTo(CREADA);
		verify(pasarela, never()).preparar(any());
	}

	@Test
	void unReintentoEnCursoRecienteRecibe202() {
		when(transacciones.iniciar(any(), any(), any(), any())).thenThrow(new DataIntegrityViolationException("uq"));
		when(transacciones.buscarClave(2L, CLAVE)).thenReturn(Optional.of(clavePrevia(ReservaService.huella(solicitud))));
		when(transacciones.buscar(50L)).thenReturn(Optional.of(reserva));

		RespuestaReserva respuesta = servicio.reservar(huesped, CLAVE, solicitud);

		assertThat(respuesta.codigoHttp()).isEqualTo(202);
		assertThat(respuesta.cuerpo()).contains("\"reservaId\":50");
	}

	@Test
	void unReintentoQueVeLaReservaYaResueltaRecibeLaRespuestaGuardada() {
		ClaveIdempotencia enCurso = clavePrevia(ReservaService.huella(solicitud));
		ClaveIdempotencia terminada = clavePrevia(ReservaService.huella(solicitud));
		terminada.terminar(201, "{\"id\":50}", AHORA);
		reserva.registrarReferencia("pi_1", AHORA);
		reserva.confirmar("pi_1", AHORA);
		when(transacciones.iniciar(any(), any(), any(), any())).thenThrow(new DataIntegrityViolationException("uq"));
		when(transacciones.buscarClave(2L, CLAVE)).thenReturn(Optional.of(enCurso), Optional.of(terminada));
		when(transacciones.buscar(50L)).thenReturn(Optional.of(reserva));

		assertThat(servicio.reservar(huesped, CLAVE, solicitud)).isEqualTo(CREADA);
	}

	@Test
	void unReintentoDeUnaReservaHuerfanaConsultaPorSuReferenciaYConfirma() {
		TestDatos.asignar(reserva, "creadoEn", AHORA.minusSeconds(60));
		reserva.registrarReferencia("pi_1", AHORA);
		when(transacciones.iniciar(any(), any(), any(), any())).thenThrow(new DataIntegrityViolationException("uq"));
		when(transacciones.buscarClave(2L, CLAVE)).thenReturn(Optional.of(clavePrevia(ReservaService.huella(solicitud))));
		when(transacciones.buscar(50L)).thenReturn(Optional.of(reserva));
		when(pasarela.consultar("pi_1")).thenReturn(ResultadoPago.aprobado("pi_1"));
		when(transacciones.confirmar(50L, "pi_1")).thenReturn(new Desenlace(CREADA, false));

		assertThat(servicio.reservar(huesped, CLAVE, solicitud)).isEqualTo(CREADA);
		verify(pasarela, never()).cobrar(any(), any());
	}

	@Test
	void unaReservaHuerfanaSinReferenciaNuncaSeCobroYSeCompensa() {
		when(transacciones.compensar(eq(50L), eq(HttpStatus.PAYMENT_REQUIRED), eq("PAGO_NO_REALIZADO"), any(),
				isNull(), eq(false), eq(false))).thenReturn(new Desenlace(FALLIDA, false));

		assertThat(servicio.recuperar(reserva)).isEqualTo(FALLIDA);
		verify(pasarela, never()).consultar(any());
	}

	@Test
	void recuperarConReferenciaSinCobroQuedaPorVerificar() {
		reserva.registrarReferencia("pi_1", AHORA);
		when(pasarela.consultar("pi_1")).thenReturn(ResultadoPago.noCobrado());
		when(transacciones.compensar(eq(50L), eq(HttpStatus.PAYMENT_REQUIRED), eq("PAGO_NO_REALIZADO"), any(),
				isNull(), eq(true), eq(false))).thenReturn(new Desenlace(FALLIDA, false));

		assertThat(servicio.recuperar(reserva)).isEqualTo(FALLIDA);
	}

	@Test
	void laMismaClaveConOtrosDatosEsUnConflicto() {
		when(transacciones.iniciar(any(), any(), any(), any())).thenThrow(new DataIntegrityViolationException("uq"));
		when(transacciones.buscarClave(2L, CLAVE)).thenReturn(Optional.of(clavePrevia("otra-huella")));

		assertThatThrownBy(() -> servicio.reservar(huesped, CLAVE, solicitud))
				.isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", "IDEMPOTENCIA_CONFLICTO");
	}

	@Test
	void laHuellaNoDependeDelOrdenDeLosServicios() {
		ReservaDTO conServicios = new ReservaDTO(null, 10L, solicitud.llegada(), solicitud.salida(), 2, Set.of(5L, 4L),
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
		ReservaDTO otroOrden = new ReservaDTO(null, 10L, solicitud.llegada(), solicitud.salida(), 2, Set.of(4L, 5L),
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

		assertThat(ReservaService.huella(conServicios)).isEqualTo(ReservaService.huella(otroOrden))
				.isNotEqualTo(ReservaService.huella(solicitud)).hasSize(64);
	}

	// ─────────────────────────────────────────
	// Conciliación — MA-60, MA-68
	// ─────────────────────────────────────────

	@Test
	void alVencerSinReferenciaSeCompensaSinConsultar() {
		servicio.vencer(reserva);

		verify(transacciones).compensar(eq(50L), eq(HttpStatus.CONFLICT), eq("RETENCION_VENCIDA"), any(), isNull(),
				eq(false), eq(false));
		verify(pasarela, never()).consultar(any());
	}

	@Test
	void alVencerLaRetencionSeCompensaYSeDevuelveSiAlcanzoACobrar() {
		reserva.registrarReferencia("pi_1", AHORA);
		when(pasarela.consultar("pi_1")).thenReturn(ResultadoPago.aprobado("pi_1"));
		when(transacciones.compensar(eq(50L), eq(HttpStatus.CONFLICT), eq("RETENCION_VENCIDA"), any(), isNull(),
				eq(false), eq(true))).thenReturn(new Desenlace(FALLIDA, true));
		when(transacciones.siguienteIntentoDevolucion(50L)).thenReturn(1);

		servicio.vencer(reserva);

		verify(pasarela).devolver("pi_1", reserva.getTotal(), CLAVE_PAGO, 1);
		verify(transacciones).marcarPagoVerificado(50L, "pi_1");
	}

	@Test
	void siOtroFlujoLaConfirmoAlVencerNoSeDevuelve() {
		reserva.registrarReferencia("pi_1", AHORA);
		when(pasarela.consultar("pi_1")).thenReturn(ResultadoPago.aprobado("pi_1"));
		when(transacciones.compensar(eq(50L), any(), any(), any(), any(), anyBoolean(), anyBoolean()))
				.thenReturn(new Desenlace(CREADA, false));

		servicio.vencer(reserva);

		verify(pasarela, never()).devolver(any(), any(), any(), anyInt());
	}

	@Test
	void alVencerSinPasarelaQuedaPorVerificar() {
		reserva.registrarReferencia("pi_1", AHORA);
		when(pasarela.consultar("pi_1")).thenThrow(new PasarelaNoDisponibleException("caída"));

		servicio.vencer(reserva);

		verify(transacciones).compensar(eq(50L), eq(HttpStatus.GATEWAY_TIMEOUT), eq("RETENCION_VENCIDA"), any(),
				isNull(), eq(true), eq(false));
	}

	@Test
	void verificarUnPagoEnDudaDevuelveSiHuboCobro() {
		reserva.registrarReferencia("pi_1", AHORA);
		when(pasarela.consultar("pi_1")).thenReturn(ResultadoPago.aprobado("pi_1"));
		when(transacciones.siguienteIntentoDevolucion(50L)).thenReturn(2);

		servicio.verificarPago(reserva);

		verify(pasarela).devolver("pi_1", reserva.getTotal(), CLAVE_PAGO, 2);
		verify(transacciones).marcarPagoVerificado(50L, "pi_1");
	}

	@Test
	void verificarUnPagoDeUnaReservaConfirmadaNoLoDevuelve() {
		reserva.registrarReferencia("pi_1", AHORA);
		reserva.confirmar("pi_1", AHORA);
		when(pasarela.consultar("pi_1")).thenReturn(ResultadoPago.aprobado("pi_1"));

		servicio.verificarPago(reserva);

		verify(pasarela, never()).devolver(any(), any(), any(), anyInt());
	}

	@Test
	void verificarUnPagoSinReferenciaSoloLoMarca() {
		servicio.verificarPago(reserva);

		verify(pasarela, never()).consultar(any());
		verify(transacciones).marcarPagoVerificado(eq(50L), isNull());
	}
}
