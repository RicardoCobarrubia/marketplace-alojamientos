package co.edu.unbosque.marketplace.cotizacion.aplicacion;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento.EstadoAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.ServicioAdicional;
import co.edu.unbosque.marketplace.alojamientos.dominio.ServicioAdicional.Modalidad;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.AlojamientoRepository;
import co.edu.unbosque.marketplace.comun.excepcion.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.cotizacion.dominio.CalculadoraCotizacion;
import co.edu.unbosque.marketplace.cotizacion.dominio.ConfiguracionCotizacion;
import co.edu.unbosque.marketplace.cotizacion.dominio.DescuentoEstadiaLarga;
import co.edu.unbosque.marketplace.cotizacion.infraestructura.ConfiguracionCotizacionRepository;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

@ExtendWith(MockitoExtension.class)
class CotizacionServiceTest {

	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
	private static final LocalDate HOY = LocalDate.ofInstant(AHORA, BOGOTA);

	@Mock
	private AlojamientoRepository alojamientoRepository;
	@Mock
	private ConfiguracionCotizacionRepository configuracionRepository;

	private CotizacionService servicio;
	private Usuario dueno;
	private Usuario huesped;
	private Alojamiento alojamiento;
	private ConfiguracionCotizacion configuracion;

	@BeforeEach
	void preparar() {
		servicio = new CotizacionService(alojamientoRepository, configuracionRepository,
				new CalculadoraCotizacion(List.of(new DescuentoEstadiaLarga())), Clock.fixed(AHORA, ZoneOffset.UTC),
				BOGOTA);
		dueno = TestDatos.usuario(1L, Rol.ANFITRION, "hash");
		huesped = TestDatos.usuario(2L, Rol.HUESPED, "hash", "huesped@correo.com");
		alojamiento = TestDatos.alojamiento(10L, dueno);
		TestDatos.asignar(alojamiento, "estado", EstadoAlojamiento.ACTIVA);
		configuracion = new ConfiguracionCotizacion(new BigDecimal("10"), new BigDecimal("10"), 7);
	}

	private void existe() {
		when(alojamientoRepository.findById(10L)).thenReturn(Optional.of(alojamiento));
	}

	private void hayConfiguracion() {
		when(configuracionRepository.findById(ConfiguracionCotizacion.ID)).thenReturn(Optional.of(configuracion));
	}

	@Test
	void cotizaConLosServiciosElegidos() {
		existe();
		hayConfiguracion();
		ServicioAdicional desayuno = alojamiento.agregarServicioAdicional("Desayuno", new BigDecimal("30000"),
				Modalidad.POR_NOCHE, AHORA);
		TestDatos.asignar(desayuno, "id", 4L);

		CotizacionDTO cotizacion = servicio.cotizar(huesped, 10L, HOY.plusDays(1), HOY.plusDays(3), Set.of(4L));

		assertThat(cotizacion.noches()).isEqualTo(2);
		assertThat(cotizacion.totalServicios()).isEqualByComparingTo("60000");
		assertThat(cotizacion.total()).isEqualByComparingTo("660000");
	}

	@Test
	void rechazaFechasInvalidas() {
		LocalDate ayer = HOY.minusDays(1);
		LocalDate manana = HOY.plusDays(1);
		Set<Long> ninguno = Set.of();

		assertThatThrownBy(() -> servicio.cotizar(huesped, 10L, ayer, manana, ninguno))
				.isInstanceOf(SolicitudInvalidaException.class)
				.hasFieldOrPropertyWithValue("codigo", "FECHAS_INVALIDAS");
		assertThatThrownBy(() -> servicio.cotizar(huesped, 10L, manana, manana, ninguno))
				.isInstanceOf(SolicitudInvalidaException.class);
		assertThatThrownBy(() -> servicio.cotizar(huesped, 10L, null, manana, ninguno))
				.isInstanceOf(SolicitudInvalidaException.class);
	}

	@Test
	void rechazaUnServicioDeOtroAlojamientoORetirado() {
		existe();
		ServicioAdicional retirado = alojamiento.agregarServicioAdicional("Traslado", new BigDecimal("50000"),
				Modalidad.POR_ESTADIA, AHORA);
		TestDatos.asignar(retirado, "id", 5L);
		retirado.retirar();
		LocalDate llegada = HOY.plusDays(1);
		LocalDate salida = HOY.plusDays(2);
		Set<Long> deOtro = Set.of(99L);
		Set<Long> retirados = Set.of(5L);

		assertThatThrownBy(() -> servicio.cotizar(huesped, 10L, llegada, salida, deOtro))
				.isInstanceOf(SolicitudInvalidaException.class)
				.hasFieldOrPropertyWithValue("codigo", "SERVICIO_NO_PERTENECE");
		assertThatThrownBy(() -> servicio.cotizar(huesped, 10L, llegada, salida, retirados))
				.isInstanceOf(SolicitudInvalidaException.class);
	}

	@Test
	void unAlojamientoNoActivoSoloLoCotizaSuDueno() {
		existe();
		hayConfiguracion();
		TestDatos.asignar(alojamiento, "estado", EstadoAlojamiento.PAUSADA);
		LocalDate llegada = HOY.plusDays(1);
		LocalDate salida = HOY.plusDays(2);

		assertThatThrownBy(() -> servicio.cotizar(huesped, 10L, llegada, salida, null))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(servicio.cotizar(dueno, 10L, llegada, salida, null).noches()).isEqualTo(1);
	}

	@Test
	void elAdministradorCambiaLaConfiguracion() {
		hayConfiguracion();
		Usuario administrador = TestDatos.usuario(9L, Rol.ADMINISTRADOR, "hash", "admin@correo.com");

		ConfiguracionCotizacionDTO resultado = servicio.actualizarConfiguracion(administrador,
				new ConfiguracionCotizacionDTO(new BigDecimal("12.5"), new BigDecimal("15"), 5, null));

		assertThat(resultado.porcentajeTarifaServicio()).isEqualByComparingTo("12.5");
		assertThat(resultado.minimoNochesDescuento()).isEqualTo(5);
		assertThat(resultado.actualizadoEn()).isEqualTo(AHORA);
		verify(configuracionRepository).saveAndFlush(configuracion);
		assertThat(servicio.obtenerConfiguracion().porcentajeDescuentoEstadiaLarga()).isEqualByComparingTo("15");
	}

	@Test
	void sinConfiguracionFallaConUnErrorClaro() {
		when(configuracionRepository.findById(ConfiguracionCotizacion.ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.obtenerConfiguracion()).isInstanceOf(IllegalStateException.class);
	}
}
