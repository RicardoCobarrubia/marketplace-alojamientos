package co.edu.unbosque.marketplace.resenas.aplicacion;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import co.edu.unbosque.marketplace.ServiciosDePrueba;
import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento.EstadoAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.AlojamientoRepository;
import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.comun.excepcion.AccesoDenegadoException;
import co.edu.unbosque.marketplace.comun.excepcion.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.comun.excepcion.ReglaNegocioException;
import co.edu.unbosque.marketplace.cotizacion.dominio.CalculadoraCotizacion;
import co.edu.unbosque.marketplace.cotizacion.dominio.ConfiguracionCotizacion;
import co.edu.unbosque.marketplace.cotizacion.dominio.DescuentoEstadiaLarga;
import co.edu.unbosque.marketplace.disponibilidad.dominio.RangoFechas;
import co.edu.unbosque.marketplace.notificaciones.aplicacion.PublicadorEventos;
import co.edu.unbosque.marketplace.resenas.dominio.Resena;
import co.edu.unbosque.marketplace.resenas.infraestructura.ResenaRepository;
import co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;
import co.edu.unbosque.marketplace.reservas.infraestructura.ReservaRepository;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

@ExtendWith(MockitoExtension.class)
class ResenaServiceTest {

	/** AHORA es el 7 de noviembre de 2026 en Bogotá. */
	private static final LocalDate HOY = LocalDate.of(2026, 11, 7);
	private static final String COMENTARIO = "Muy buena estadía, recomendado.";

	@Mock
	private ResenaRepository resenas;
	@Mock
	private ReservaRepository reservas;
	@Mock
	private AlojamientoRepository alojamientos;
	@Mock
	private PublicadorEventos publicador;
	@Mock
	private Auditoria auditoria;

	private ResenaService servicio;
	private Usuario anfitrion;
	private Usuario huesped;
	private Alojamiento alojamiento;
	private Reserva reserva;

	@BeforeEach
	void preparar() {
		servicio = new ResenaService(resenas, ServiciosDePrueba.reservas(reservas),
				ServiciosDePrueba.alojamientos(alojamientos), publicador, auditoria,
				Clock.fixed(AHORA, ZoneOffset.UTC), ZoneId.of("America/Bogota"));
		anfitrion = TestDatos.usuario(1L, Rol.ANFITRION, "hash");
		huesped = TestDatos.usuario(2L, Rol.HUESPED, "hash", "huesped@correo.com");
		alojamiento = TestDatos.alojamiento(10L, anfitrion);
		reserva = completadaConSalida(HOY.minusDays(3));
	}

	private Reserva completadaConSalida(LocalDate salida) {
		Reserva completada = new Reserva(huesped, alojamiento, new RangoFechas(salida.minusDays(2), salida), 2,
				new CalculadoraCotizacion(List.of(new DescuentoEstadiaLarga())).calcular(alojamiento, 2, List.of(),
						new ConfiguracionCotizacion(BigDecimal.TEN, BigDecimal.TEN, 7)),
				null, AHORA);
		TestDatos.asignar(completada, "id", 50L);
		completada.confirmar("pi_1", AHORA);
		completada.completar(salida, AHORA);
		return completada;
	}

	private Resena guardada(int calificacion) {
		Resena resena = new Resena(50L, 10L, 2L, calificacion, COMENTARIO, AHORA);
		TestDatos.asignar(resena, "id", 7L);
		return resena;
	}

	// ─────────────────────────────────────────
	// Publicar — MA-77, MA-78
	// ─────────────────────────────────────────

	@Test
	void elHuespedResenaSuEstadiaCompletadaYQuedaAuditada() {
		when(reservas.findById(50L)).thenReturn(Optional.of(reserva));
		when(resenas.saveAndFlush(any(Resena.class))).thenAnswer(invocacion -> {
			Resena resena = invocacion.getArgument(0);
			TestDatos.asignar(resena, "id", 7L);
			return resena;
		});

		ResenaDTO publicada = servicio.publicar(huesped, 50L, 5, COMENTARIO);

		assertThat(publicada.reservaId()).isEqualTo(50L);
		assertThat(publicada.alojamientoId()).isEqualTo(10L);
		assertThat(publicada.huespedId()).isEqualTo(2L);
		verify(auditoria).registrar(EventoAuditoria.cambio("PUBLICAR_RESENA", "RESENA", 7L, null, "VISIBLE",
				"Calificación 5"));
		verify(publicador).publicar(eq("RESENA_PUBLICADA"), eq("RESENA"), eq(7L), anyMap());
	}

	@Test
	void elUltimoDiaDelPlazoTodaviaSePuedeResenar() {
		reserva = completadaConSalida(HOY.minusDays(30));
		when(reservas.findById(50L)).thenReturn(Optional.of(reserva));
		when(resenas.saveAndFlush(any(Resena.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		assertThat(servicio.publicar(huesped, 50L, 4, COMENTARIO).calificacion()).isEqualTo(4);
	}

	@Test
	void fueraDelPlazoSeRechaza() {
		reserva = completadaConSalida(HOY.minusDays(31));
		when(reservas.findById(50L)).thenReturn(Optional.of(reserva));

		assertThatThrownBy(() -> servicio.publicar(huesped, 50L, 4, COMENTARIO))
				.isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", "PLAZO_VENCIDO");
	}

	@Test
	void unaReservaNoCompletadaSeRechaza() {
		TestDatos.asignar(reserva, "estado", EstadoReserva.CONFIRMADA);
		when(reservas.findById(50L)).thenReturn(Optional.of(reserva));

		assertThatThrownBy(() -> servicio.publicar(huesped, 50L, 4, COMENTARIO))
				.hasFieldOrPropertyWithValue("codigo", "RESENA_NO_PERMITIDA");
	}

	@Test
	void unaReservaAjenaNoExisteYElAnfitrionNoPuedeResenar() {
		when(reservas.findById(50L)).thenReturn(Optional.of(reserva));
		Usuario otro = TestDatos.usuario(9L, Rol.HUESPED, "hash", "otro@correo.com");

		assertThatThrownBy(() -> servicio.publicar(otro, 50L, 4, COMENTARIO))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> servicio.publicar(anfitrion, 50L, 4, COMENTARIO))
				.isInstanceOf(AccesoDenegadoException.class);
		verify(resenas, never()).saveAndFlush(any());
	}

	@Test
	void unaSegundaResenaDeLaMismaReservaSeRechaza() {
		when(reservas.findById(50L)).thenReturn(Optional.of(reserva));
		when(resenas.existsByReservaId(50L)).thenReturn(false, true);
		when(resenas.saveAndFlush(any(Resena.class))).thenThrow(new DataIntegrityViolationException("uq"));

		// Dos envíos a la vez: la restricción única decide
		assertThatThrownBy(() -> servicio.publicar(huesped, 50L, 4, COMENTARIO))
				.hasFieldOrPropertyWithValue("codigo", "RESENA_DUPLICADA");
		// Ya existe: se rechaza antes de guardar
		assertThatThrownBy(() -> servicio.publicar(huesped, 50L, 4, COMENTARIO))
				.hasFieldOrPropertyWithValue("codigo", "RESENA_DUPLICADA");
	}

	// ─────────────────────────────────────────
	// Consultar — MA-78, escenario 3
	// ─────────────────────────────────────────

	@Test
	void elDetalleMuestraLasVisiblesConSuPromedio() {
		alojamiento.enviarARevision(AHORA);
		alojamiento.cambiarEstado(EstadoAlojamiento.ACTIVA, AHORA);
		when(alojamientos.findById(10L)).thenReturn(Optional.of(alojamiento));
		when(resenas.findByAlojamientoIdAndOcultaFalseOrderByCreadaEnDescIdDesc(10L))
				.thenReturn(List.of(guardada(5), guardada(4), guardada(4)));

		ResenasAlojamientoDTO detalle = servicio.delAlojamiento(10L);

		assertThat(detalle.promedio()).isEqualByComparingTo("4.3");
		assertThat(detalle.cantidad()).isEqualTo(3);
		assertThat(detalle.resenas()).hasSize(3);
	}

	@Test
	void sinResenasNoHayPromedioYUnAlojamientoNoPublicadoNoSeMuestra() {
		alojamiento.enviarARevision(AHORA);
		alojamiento.cambiarEstado(EstadoAlojamiento.ACTIVA, AHORA);
		when(alojamientos.findById(10L)).thenReturn(Optional.of(alojamiento));
		when(resenas.findByAlojamientoIdAndOcultaFalseOrderByCreadaEnDescIdDesc(10L)).thenReturn(List.of());

		ResenasAlojamientoDTO vacio = servicio.delAlojamiento(10L);
		assertThat(vacio.promedio()).isNull();
		assertThat(vacio.resenas()).isEmpty();

		alojamiento.cambiarEstado(EstadoAlojamiento.PAUSADA, AHORA);
		assertThatThrownBy(() -> servicio.delAlojamiento(10L)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	// ─────────────────────────────────────────
	// Moderar — MA-79
	// ─────────────────────────────────────────

	@Test
	void ocultarYMostrarQuedanAuditadosYSeNotificanAlHuesped() {
		Resena resena = guardada(1);
		when(resenas.buscarParaModerar(7L)).thenReturn(Optional.of(resena));
		when(alojamientos.findById(10L)).thenReturn(Optional.of(alojamiento));

		assertThat(servicio.ocultar(7L, "Lenguaje ofensivo").oculta()).isTrue();
		verify(auditoria).registrar(EventoAuditoria.cambio("OCULTAR_RESENA", "RESENA", 7L, "VISIBLE", "OCULTA",
				"Lenguaje ofensivo"));
		verify(publicador).publicar(eq("RESENA_OCULTA"), eq("RESENA"), eq(7L), anyMap());

		assertThat(servicio.mostrar(7L).oculta()).isFalse();
		verify(auditoria).registrar(EventoAuditoria.cambio("MOSTRAR_RESENA", "RESENA", 7L, "OCULTA", "VISIBLE",
				null));
		verify(publicador).publicar(eq("RESENA_VISIBLE"), eq("RESENA"), eq(7L), anyMap());
	}

	@Test
	void moderarUnaResenaQueNoExisteResponde404() {
		when(resenas.buscarParaModerar(8L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.ocultar(8L, "Motivo")).isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void elAdministradorListaTodasOFiltraPorVisibilidad() {
		when(resenas.findAllByOrderByCreadaEnDescIdDesc()).thenReturn(List.of(guardada(5)));
		when(resenas.findByOcultaOrderByCreadaEnDescIdDesc(true)).thenReturn(List.of());

		assertThat(servicio.listar(null)).hasSize(1);
		assertThat(servicio.listar(true)).isEmpty();
	}
}
