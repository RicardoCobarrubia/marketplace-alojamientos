package co.edu.unbosque.marketplace.reservas.aplicacion;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import co.edu.unbosque.marketplace.comun.excepcion.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.cotizacion.dominio.CalculadoraCotizacion;
import co.edu.unbosque.marketplace.cotizacion.dominio.ConfiguracionCotizacion;
import co.edu.unbosque.marketplace.cotizacion.dominio.DescuentoEstadiaLarga;
import co.edu.unbosque.marketplace.disponibilidad.dominio.RangoFechas;
import co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;
import co.edu.unbosque.marketplace.reservas.infraestructura.ReservaRepository;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

@ExtendWith(MockitoExtension.class)
class RevisionReservasServiceTest {

	/** AHORA es el 7 de noviembre de 2026 en Bogotá. */
	private static final LocalDate HOY = LocalDate.of(2026, 11, 7);

	@Mock
	private ReservaRepository reservaRepository;
	@Mock
	private EventosReserva eventos;

	private RevisionReservasService servicio;
	private Reserva reserva;

	@BeforeEach
	void preparar() {
		servicio = new RevisionReservasService(reservaRepository, eventos, Clock.fixed(AHORA, ZoneOffset.UTC),
				ZoneId.of("America/Bogota"));
		Alojamiento alojamiento = TestDatos.alojamiento(10L, TestDatos.usuario(1L, Rol.ANFITRION, "hash"));
		reserva = new Reserva(TestDatos.usuario(2L, Rol.HUESPED, "hash", "huesped@correo.com"), alojamiento,
				new RangoFechas(LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 22)), 2,
				new CalculadoraCotizacion(List.of(new DescuentoEstadiaLarga())).calcular(alojamiento, 2, List.of(),
						new ConfiguracionCotizacion(BigDecimal.TEN, BigDecimal.TEN, 7)),
				null, AHORA);
		TestDatos.asignar(reserva, "id", 50L);
		reserva.confirmar("pi_1", AHORA);
	}

	@Test
	void lasReservasFuturasDelAlojamientoPasanARevisionConEventoYAuditoria() {
		when(reservaRepository.bloquearDelAlojamientoDesde(10L, EstadoReserva.CONFIRMADA, HOY))
				.thenReturn(List.of(reserva));

		assertThat(servicio.ponerEnRevisionDelAlojamiento(10L, "Bloqueado")).isEqualTo(1);

		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.EN_REVISION);
		verify(eventos).publicar("RESERVA_EN_REVISION", reserva, EstadoReserva.CONFIRMADA, Map.of("motivo", "Bloqueado"));
	}

	@Test
	void unaReservaConfirmadaDuranteLaModeracionTambienSeAudita() {
		servicio.ponerEnRevisionAlConfirmar(reserva, "Durante el pago");

		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.EN_REVISION);
		verify(eventos).publicar("RESERVA_EN_REVISION", reserva, EstadoReserva.CONFIRMADA,
				Map.of("motivo", "Durante el pago"));
	}

	@Test
	void lasDelUsuarioTambien() {
		when(reservaRepository.bloquearDelUsuarioDesde(2L, EstadoReserva.CONFIRMADA, HOY)).thenReturn(List.of());

		assertThat(servicio.ponerEnRevisionDelUsuario(2L, "Restringido")).isZero();
	}

	@Test
	void listaLasReservasEnRevision() {
		reserva.ponerEnRevision(AHORA);
		when(reservaRepository.findAll(any(Specification.class), eq(Sort.by(Sort.Direction.DESC, "creadoEn", "id"))))
				.thenReturn(List.of(reserva));

		assertThat(servicio.enRevision()).singleElement()
				.satisfies(dto -> assertThat(dto.estado()).isEqualTo(EstadoReserva.EN_REVISION));
	}

	@Test
	void mantenerLaDevuelveAConfirmada() {
		reserva.ponerEnRevision(AHORA);
		when(reservaRepository.buscarParaActualizar(50L)).thenReturn(Optional.of(reserva));

		assertThat(servicio.mantener(50L).estado()).isEqualTo(EstadoReserva.CONFIRMADA);
		verify(eventos).publicar("RESERVA_MANTENIDA", reserva, EstadoReserva.EN_REVISION);
	}

	@Test
	void mantenerUnaReservaQueNoExisteResponde404() {
		when(reservaRepository.buscarParaActualizar(51L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.mantener(51L)).isInstanceOf(RecursoNoEncontradoException.class);
	}
}
