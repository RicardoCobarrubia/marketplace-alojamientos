package co.edu.unbosque.marketplace.disponibilidad.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento.EstadoAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.AlojamientoRepository;
import co.edu.unbosque.marketplace.comun.excepcion.AccesoDenegadoException;
import co.edu.unbosque.marketplace.comun.excepcion.ReglaNegocioException;
import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.disponibilidad.dominio.PeriodoDisponible;
import co.edu.unbosque.marketplace.disponibilidad.dominio.PeriodoReservado;
import co.edu.unbosque.marketplace.disponibilidad.dominio.RangoFechas;
import co.edu.unbosque.marketplace.disponibilidad.infraestructura.PeriodoDisponibleRepository;
import co.edu.unbosque.marketplace.disponibilidad.infraestructura.PeriodoReservadoRepository;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

@ExtendWith(MockitoExtension.class)
class DisponibilidadServiceTest {

	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
	/** 7 de noviembre de 2026 a mediodía en Bogotá. */
	private static final Instant AHORA = Instant.parse("2026-11-07T17:00:00Z");
	private static final LocalDate HOY = LocalDate.of(2026, 11, 7);

	@Mock
	private AlojamientoRepository alojamientoRepository;
	@Mock
	private PeriodoDisponibleRepository periodoRepository;
	@Mock
	private PeriodoReservadoRepository reservadoRepository;

	private DisponibilidadService servicio;
	private final Usuario dueno = TestDatos.usuario(1L, Rol.ANFITRION, "h", "dueno@correo.com");
	private final Usuario otro = TestDatos.usuario(2L, Rol.ANFITRION, "h", "otro@correo.com");
	private final Usuario huesped = TestDatos.usuario(4L, Rol.HUESPED, "h", "huesped@correo.com");
	private Alojamiento alojamiento;

	@BeforeEach
	void preparar() {
		servicio = new DisponibilidadService(alojamientoRepository, periodoRepository, reservadoRepository,
				Clock.fixed(AHORA, BOGOTA), BOGOTA);
		alojamiento = TestDatos.alojamiento(10L, dueno);
		TestDatos.asignar(alojamiento, "estado", EstadoAlojamiento.ACTIVA);
	}

	private void existe() {
		// Consultar usa findById; crear, editar y eliminar bloquean la fila con buscarParaActualizar
		lenient().when(alojamientoRepository.findById(10L)).thenReturn(Optional.of(alojamiento));
		lenient().when(alojamientoRepository.buscarParaActualizar(10L)).thenReturn(Optional.of(alojamiento));
	}

	private static PeriodoDisponibleDTO periodo(LocalDate inicio, LocalDate fin) {
		return new PeriodoDisponibleDTO(null, inicio, fin);
	}

	private PeriodoReservado reservado(LocalDate llegada, LocalDate salida) {
		return new PeriodoReservado(alojamiento, 99L, new RangoFechas(llegada, salida), null, AHORA);
	}

	// ─────────────────────────────────────────
	// Consultar — MA-59
	// ─────────────────────────────────────────

	@Test
	void rechazaLlegadaAnteriorAHoyYSalidaNoPosterior() {
		LocalDate ayer = HOY.minusDays(1);
		LocalDate pasadoManana = HOY.plusDays(2);
		LocalDate enTresDias = HOY.plusDays(3);

		assertThatThrownBy(() -> servicio.consultar(huesped, 10L, ayer, pasadoManana))
				.isInstanceOf(SolicitudInvalidaException.class)
				.hasFieldOrPropertyWithValue("codigo", "FECHAS_INVALIDAS");
		assertThatThrownBy(() -> servicio.consultar(huesped, 10L, enTresDias, enTresDias))
				.isInstanceOf(SolicitudInvalidaException.class);
	}

	@Test
	void estaDisponibleSiCabeEnUnPeriodoYNoHayReservasVigentes() {
		existe();
		when(periodoRepository.algunoContiene(10L, HOY, HOY.plusDays(3))).thenReturn(true);
		when(reservadoRepository.buscarVigentesQueSeCruzan(10L, HOY, HOY.plusDays(3), AHORA)).thenReturn(List.of());

		DisponibilidadDTO respuesta = servicio.consultar(null, 10L, HOY, HOY.plusDays(3));

		assertThat(respuesta.disponible()).isTrue();
		assertThat(respuesta.noches()).isEqualTo(3);
		assertThat(respuesta.motivo()).isNull();
	}

	@Test
	void indicaPorQueNoEstaDisponible() {
		existe();
		when(periodoRepository.algunoContiene(eq(10L), any(), any())).thenReturn(false, true);
		when(reservadoRepository.buscarVigentesQueSeCruzan(eq(10L), any(), any(), eq(AHORA)))
				.thenReturn(List.of(reservado(HOY.plusDays(1), HOY.plusDays(2))));

		assertThat(servicio.consultar(huesped, 10L, HOY, HOY.plusDays(3)).motivo()).isEqualTo("FUERA_DE_PERIODO");
		assertThat(servicio.consultar(huesped, 10L, HOY, HOY.plusDays(3)).motivo()).isEqualTo("OCUPADO");

		TestDatos.asignar(alojamiento, "estado", EstadoAlojamiento.PAUSADA);
		assertThat(servicio.consultar(dueno, 10L, HOY, HOY.plusDays(3)).motivo()).isEqualTo("NO_ACTIVO");
	}

	// ─────────────────────────────────────────
	// Periodos — MA-58
	// ─────────────────────────────────────────

	@Test
	void elDuenoCreaUnPeriodo() {
		existe();
		when(periodoRepository.existeOtroQueSeCruza(10L, -1L, HOY, HOY.plusDays(30))).thenReturn(false);
		when(periodoRepository.saveAndFlush(any(PeriodoDisponible.class))).thenAnswer(inv -> inv.getArgument(0));

		PeriodoDisponibleDTO creado = servicio.crearPeriodo(dueno, 10L, periodo(HOY, HOY.plusDays(30)));

		assertThat(creado.fechaFin()).isEqualTo(HOY.plusDays(30));
	}

	@Test
	void rechazaFechasInvalidasPeriodosCruzadosYOtroUsuario() {
		existe();
		PeriodoDisponibleDTO sinNoches = periodo(HOY.plusDays(5), HOY.plusDays(5));
		PeriodoDisponibleDTO desdeAyer = periodo(HOY.minusDays(1), HOY.plusDays(5));
		PeriodoDisponibleDTO valido = periodo(HOY, HOY.plusDays(5));

		assertThatThrownBy(() -> servicio.crearPeriodo(dueno, 10L, sinNoches))
				.isInstanceOf(SolicitudInvalidaException.class);
		assertThatThrownBy(() -> servicio.crearPeriodo(dueno, 10L, desdeAyer))
				.isInstanceOf(SolicitudInvalidaException.class);

		when(periodoRepository.existeOtroQueSeCruza(eq(10L), eq(-1L), any(), any())).thenReturn(true);
		assertThatThrownBy(() -> servicio.crearPeriodo(dueno, 10L, valido))
				.isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", "PERIODO_SUPERPUESTO");

		assertThatThrownBy(() -> servicio.crearPeriodo(otro, 10L, valido))
				.isInstanceOf(AccesoDenegadoException.class);
	}

	@Test
	void noSePuedeReducirNiEliminarUnPeriodoConReservas() {
		existe();
		PeriodoDisponible actual = new PeriodoDisponible(alojamiento, new RangoFechas(HOY, HOY.plusDays(30)));
		TestDatos.asignar(actual, "id", 5L);
		when(periodoRepository.findById(5L)).thenReturn(Optional.of(actual));
		when(periodoRepository.existeOtroQueSeCruza(eq(10L), eq(5L), any(), any())).thenReturn(false);
		when(reservadoRepository.buscarVigentesQueSeCruzan(10L, HOY, HOY.plusDays(30), AHORA))
				.thenReturn(List.of(reservado(HOY.plusDays(20), HOY.plusDays(25))));

		PeriodoDisponibleDTO reducido = periodo(HOY, HOY.plusDays(10));

		assertThatThrownBy(() -> servicio.editarPeriodo(dueno, 10L, 5L, reducido))
				.isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", "PERIODO_CON_RESERVAS");
		assertThatThrownBy(() -> servicio.eliminarPeriodo(dueno, 10L, 5L))
				.isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", "PERIODO_CON_RESERVAS");
		verify(periodoRepository, never()).delete(any());

		when(periodoRepository.saveAndFlush(actual)).thenReturn(actual);
		PeriodoDisponibleDTO ampliado = servicio.editarPeriodo(dueno, 10L, 5L, periodo(HOY, HOY.plusDays(40)));
		assertThat(ampliado.fechaFin()).isEqualTo(HOY.plusDays(40));
	}

	@Test
	void unaRetencionVencidaNoCuentaComoReserva() {
		PeriodoReservado retencion = new PeriodoReservado(alojamiento, 1L, new RangoFechas(HOY, HOY.plusDays(2)),
				AHORA.minusSeconds(1), AHORA.minusSeconds(600));
		assertThat(retencion.estaVigente(AHORA)).isFalse();

		retencion.bloquearDefinitivamente();
		assertThat(retencion.estaVigente(AHORA)).isTrue();

		retencion.liberar(AHORA);
		assertThat(retencion.estaVigente(AHORA)).isFalse();
	}
}
