package co.edu.unbosque.marketplace.reservas.aplicacion;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.comun.excepcion.AccesoDenegadoException;
import co.edu.unbosque.marketplace.comun.excepcion.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.cotizacion.dominio.CalculadoraCotizacion;
import co.edu.unbosque.marketplace.cotizacion.dominio.ConfiguracionCotizacion;
import co.edu.unbosque.marketplace.cotizacion.dominio.DescuentoEstadiaLarga;
import co.edu.unbosque.marketplace.disponibilidad.dominio.PeriodoReservado;
import co.edu.unbosque.marketplace.disponibilidad.dominio.RangoFechas;
import co.edu.unbosque.marketplace.disponibilidad.infraestructura.PeriodoReservadoRepository;
import co.edu.unbosque.marketplace.cotizacion.dominio.Dinero;
import co.edu.unbosque.marketplace.reservas.dominio.CalculadoraDevolucion;
import co.edu.unbosque.marketplace.reservas.dominio.DevolucionFlexible;
import co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;
import co.edu.unbosque.marketplace.reservas.infraestructura.ReservaRepository;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class GestionReservasServiceTest {

	/** AHORA es el 7 de noviembre de 2026; la reserva es del 20 al 22 de diciembre. */
	private static final RangoFechas RANGO = new RangoFechas(LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 22));

	@Mock
	private ReservaRepository reservaRepository;
	@Mock
	private PeriodoReservadoRepository periodoReservadoRepository;
	@Mock
	private EventosReserva eventos;

	private GestionReservasService servicio;
	private Usuario anfitrion;
	private Usuario huesped;
	private Reserva reserva;

	@BeforeEach
	void preparar() {
		servicio = new GestionReservasService(reservaRepository, periodoReservadoRepository, eventos,
				new CalculadoraDevolucion(List.of(new DevolucionFlexible())), JsonMapper.builder().build(),
				Clock.fixed(AHORA, ZoneOffset.UTC), ZoneId.of("America/Bogota"));
		anfitrion = TestDatos.usuario(1L, Rol.ANFITRION, "hash");
		huesped = TestDatos.usuario(2L, Rol.HUESPED, "hash", "huesped@correo.com");
		Alojamiento alojamiento = TestDatos.alojamiento(10L, anfitrion);
		reserva = new Reserva(huesped, alojamiento, RANGO, 2,
				new CalculadoraCotizacion(List.of(new DescuentoEstadiaLarga())).calcular(alojamiento, 2, List.of(),
						new ConfiguracionCotizacion(BigDecimal.TEN, BigDecimal.TEN, 7)),
				null, AHORA);
		TestDatos.asignar(reserva, "id", 50L);
		TestDatos.asignar(reserva, "politicaCancelacion",
				"{\"horasAntesParaReembolsoTotal\": 48, \"porcentajeReembolsoDespues\": 50}");
		reserva.confirmar("pi_1", AHORA);
	}

	private void seCancela() {
		when(reservaRepository.buscarParaActualizar(50L)).thenReturn(Optional.of(reserva));
		when(periodoReservadoRepository.bloquearPorReserva(50L)).thenReturn(Optional.empty());
	}

	// ─────────────────────────────────────────
	// Consultar — MA-70
	// ─────────────────────────────────────────

	@Test
	void cadaRolConsultaConSuFiltroYLasMasRecientesPrimero() {
		when(reservaRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(reserva));

		assertThat(servicio.listar(huesped, null)).singleElement()
				.satisfies(dto -> assertThat(dto.estado()).isEqualTo(EstadoReserva.CONFIRMADA));
		assertThat(servicio.listar(anfitrion, EstadoReserva.CONFIRMADA)).hasSize(1);
		assertThat(servicio.listar(TestDatos.usuario(3L, Rol.ADMINISTRADOR, "hash"), null)).hasSize(1);

		verify(reservaRepository, times(3)).findAll(any(Specification.class),
				eq(Sort.by(Sort.Direction.DESC, "creadoEn", "id")));
	}

	// ─────────────────────────────────────────
	// Cancelar — MA-72
	// ─────────────────────────────────────────

	@Test
	void alCancelarLiberaLasFechasYGuardaElEvento() {
		PeriodoReservado fechas = new PeriodoReservado(reserva.getAlojamiento(), 50L, RANGO, null, AHORA);
		when(reservaRepository.buscarParaActualizar(50L)).thenReturn(Optional.of(reserva));
		when(periodoReservadoRepository.bloquearPorReserva(50L)).thenReturn(Optional.of(fechas));

		ReservaDTO cancelada = servicio.cancelar(huesped, 50L, "Cambio de planes");

		assertThat(cancelada.estado()).isEqualTo(EstadoReserva.CANCELADA);
		assertThat(cancelada.motivoCancelacion()).isEqualTo("Cambio de planes");
		assertThat(cancelada.devolucionPendiente()).isTrue();
		assertThat(fechas.getLiberadoEn()).isEqualTo(AHORA);
		verify(eventos).publicar("RESERVA_CANCELADA", reserva, EstadoReserva.CONFIRMADA, Map.of("motivo", "Cambio de planes",
				"canceladaPor", Rol.HUESPED, "valorDevolucion", reserva.getTotal()));
	}

	@Test
	void conCuarentaYOchoHorasOMasSeDevuelveElTotal() {
		seCancela();

		assertThat(servicio.cancelar(huesped, 50L, "Motivo").valorDevolucion()).isEqualTo(reserva.getTotal());
	}

	@Test
	void conMenosDeCuarentaYOchoHorasSeDevuelveLaMitad() {
		// AHORA es el 7 de noviembre a las 7:00 en Bogotá: faltan 17 horas para el día de llegada
		TestDatos.asignar(reserva, "fechaLlegada", LocalDate.of(2026, 11, 8));
		seCancela();

		assertThat(servicio.cancelar(huesped, 50L, "Motivo").valorDevolucion())
				.isEqualTo(Dinero.porcentaje(reserva.getTotal(), BigDecimal.valueOf(50)));
	}

	@Test
	void siCancelaElAdministradorSeDevuelveElTotalSinImportarLaAnticipacion() {
		TestDatos.asignar(reserva, "fechaLlegada", LocalDate.of(2026, 11, 8));
		seCancela();
		Usuario administrador = TestDatos.usuario(3L, Rol.ADMINISTRADOR, "hash", "admin@correo.com");

		ReservaDTO cancelada = servicio.cancelar(administrador, 50L, "Fraude");

		assertThat(cancelada.valorDevolucion()).isEqualTo(reserva.getTotal());
		assertThat(reserva.getCanceladaPor()).isEqualTo(3L);
	}

	@Test
	void laReservaDeOtroNoExisteParaQuienNoLaPuedeVer() {
		when(reservaRepository.buscarParaActualizar(50L)).thenReturn(Optional.of(reserva));
		Usuario otro = TestDatos.usuario(9L, Rol.HUESPED, "hash", "otro@correo.com");

		assertThatThrownBy(() -> servicio.cancelar(otro, 50L, "Motivo"))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
	}

	@Test
	void elAnfitrionNoPuedeCancelar() {
		when(reservaRepository.buscarParaActualizar(50L)).thenReturn(Optional.of(reserva));

		assertThatThrownBy(() -> servicio.cancelar(anfitrion, 50L, "Motivo"))
				.isInstanceOf(AccesoDenegadoException.class);
		verify(eventos, never()).publicar(anyString(), any(), any(), anyMap());
	}

	// ─────────────────────────────────────────
	// Completar — MA-71
	// ─────────────────────────────────────────

	@Test
	void buscaLasConfirmadasConSalidaHastaHoyEnLaZonaDelMarketplace() {
		when(reservaRepository.buscarPorEstadoConSalidaHasta(EstadoReserva.CONFIRMADA, LocalDate.of(2026, 11, 7)))
				.thenReturn(List.of(50L));

		assertThat(servicio.porCompletar()).containsExactly(50L);
	}

	@Test
	void completaSoloSiLaSalidaYaLlego() {
		when(reservaRepository.buscarParaActualizar(50L)).thenReturn(Optional.of(reserva));
		assertThat(servicio.completar(50L)).isFalse();

		TestDatos.asignar(reserva, "fechaSalida", LocalDate.of(2026, 11, 7));
		assertThat(servicio.completar(50L)).isTrue();
		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.COMPLETADA);
		verify(eventos).publicar("RESERVA_COMPLETADA", reserva, EstadoReserva.CONFIRMADA);
	}

	@Test
	void unaReservaQueYaNoExisteNoSeCompleta() {
		when(reservaRepository.buscarParaActualizar(51L)).thenReturn(Optional.empty());

		assertThat(servicio.completar(51L)).isFalse();
	}
}
