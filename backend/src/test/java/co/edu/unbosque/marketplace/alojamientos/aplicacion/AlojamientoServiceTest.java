package co.edu.unbosque.marketplace.alojamientos.aplicacion;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
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
import co.edu.unbosque.marketplace.alojamientos.dominio.DatosAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.ServicioAdicional.Modalidad;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.AlojamientoRepository;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.CiudadRepository;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.ServicioAdicionalRepository;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.ServicioRepository;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.TipoAlojamientoRepository;
import co.edu.unbosque.marketplace.comun.excepcion.AccesoDenegadoException;
import co.edu.unbosque.marketplace.comun.excepcion.InformacionIncompletaException;
import co.edu.unbosque.marketplace.comun.excepcion.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.comun.excepcion.ReglaNegocioException;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;
import co.edu.unbosque.marketplace.usuarios.infraestructura.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class AlojamientoServiceTest {

	@Mock
	private AlojamientoRepository alojamientoRepository;
	@Mock
	private ServicioAdicionalRepository servicioAdicionalRepository;
	@Mock
	private UsuarioRepository usuarioRepository;
	@Mock
	private CiudadRepository ciudadRepository;
	@Mock
	private TipoAlojamientoRepository tipoRepository;
	@Mock
	private ServicioRepository servicioRepository;
	@Mock
	private PoliticaCancelacionRepository politicaRepository;

	private AlojamientoService servicio;

	private final Usuario dueno = TestDatos.usuario(1L, Rol.ANFITRION, "h", "dueno@correo.com");
	private final Usuario otroAnfitrion = TestDatos.usuario(2L, Rol.ANFITRION, "h", "otro@correo.com");
	private final Usuario administrador = TestDatos.usuario(3L, Rol.ADMINISTRADOR, "h", "admin@correo.com");
	private final Usuario huesped = TestDatos.usuario(4L, Rol.HUESPED, "h", "huesped@correo.com");
	private Alojamiento alojamiento;

	@BeforeEach
	void preparar() {
		servicio = new AlojamientoService(alojamientoRepository, servicioAdicionalRepository, usuarioRepository,
				ciudadRepository, tipoRepository, servicioRepository, politicaRepository,
				Clock.fixed(AHORA, ZoneOffset.UTC));
		alojamiento = TestDatos.alojamiento(10L, dueno);
	}

	private void existe(Alojamiento alojamiento) {
		when(alojamientoRepository.findById(10L)).thenReturn(Optional.of(alojamiento));
	}

	/** Prepara los catálogos que resuelve una petición de edición. */
	private AlojamientoDTO solicitudValida() {
		DatosAlojamiento datos = TestDatos.datosAlojamiento();
		when(servicioRepository.findAllById(Set.of((short) 1))).thenReturn(List.copyOf(datos.servicios()));
		when(tipoRepository.findById((short) 1)).thenReturn(Optional.of(datos.tipo()));
		when(ciudadRepository.findById(1L)).thenReturn(Optional.of(datos.ciudad()));
		when(politicaRepository.findById((short) 1)).thenReturn(Optional.of(datos.politicaCancelacion()));
		return new AlojamientoDTO(null, null, null, "Título editado", "Descripción", "Ubicación", 1L, null,
				(short) 1, null, (short) 1, null, 3, new BigDecimal("300000"), BigDecimal.ZERO, Set.of((short) 1),
				null, List.of("No fumar"), List.of("https://imagenes.ejemplo.com/1.jpg"), null, null, null, null,
				null);
	}

	// ─────────────────────────────────────────
	// Editar — MA-54
	// ─────────────────────────────────────────

	@Test
	void elDuenoPuedeEditar() {
		existe(alojamiento);

		AlojamientoDTO respuesta = servicio.editar(dueno, 10L, solicitudValida());

		assertThat(respuesta.titulo()).isEqualTo("Título editado");
		assertThat(respuesta.capacidad()).isEqualTo(3);
	}

	@Test
	void otroAnfitrionNoPuedeEditar() {
		existe(alojamiento);

		assertThatThrownBy(() -> servicio.editar(otroAnfitrion, 10L, null))
				.isInstanceOf(AccesoDenegadoException.class);
	}

	@Test
	void unBloqueadoSoloLoEditaElAdministrador() {
		TestDatos.asignar(alojamiento, "estado", EstadoAlojamiento.BLOQUEADA);
		existe(alojamiento);

		assertThatThrownBy(() -> servicio.editar(dueno, 10L, null))
				.isInstanceOf(AccesoDenegadoException.class);
		assertThat(servicio.editar(administrador, 10L, solicitudValida()).titulo()).isEqualTo("Título editado");
	}

	// ─────────────────────────────────────────
	// Consultar y estados — MA-56
	// ─────────────────────────────────────────

	@Test
	void unBorradorNoLoVeOtroUsuarioPeroSiElDuenoYElAdministrador() {
		existe(alojamiento);

		assertThatThrownBy(() -> servicio.obtener(huesped, 10L)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(servicio.obtener(dueno, 10L).id()).isEqualTo(10L);
		assertThat(servicio.obtener(administrador, 10L).id()).isEqualTo(10L);
	}

	@Test
	void conInformacionIncompletaNoSeEnviaARevisionEIndicaLosCampos() {
		TestDatos.asignar(alojamiento, "capacidad", 0);
		existe(alojamiento);

		assertThatThrownBy(() -> servicio.enviarARevision(dueno, 10L))
				.isInstanceOf(InformacionIncompletaException.class)
				.hasFieldOrPropertyWithValue("campos", List.of("capacidad"));
		assertThat(alojamiento.getEstado()).isEqualTo(EstadoAlojamiento.BORRADOR);
	}

	@Test
	void soloElDuenoEnviaARevision() {
		existe(alojamiento);

		assertThatThrownBy(() -> servicio.enviarARevision(otroAnfitrion, 10L))
				.isInstanceOf(AccesoDenegadoException.class);
		assertThat(servicio.enviarARevision(dueno, 10L).estado()).isEqualTo(EstadoAlojamiento.PENDIENTE_REVISION);
	}

	// ─────────────────────────────────────────
	// Revisión — MA-57
	// ─────────────────────────────────────────

	@Test
	void elAdministradorApruebaORechazaSoloLosPendientes() {
		existe(alojamiento);
		assertThatThrownBy(() -> servicio.aprobar(administrador, 10L))
				.isInstanceOf(ReglaNegocioException.class)
				.hasFieldOrPropertyWithValue("codigo", "NO_PENDIENTE");

		alojamiento.enviarARevision(AHORA);
		AlojamientoDTO rechazado = servicio.rechazar(administrador, 10L, "Faltan fotos del baño");

		assertThat(rechazado.estado()).isEqualTo(EstadoAlojamiento.BORRADOR);
		assertThat(rechazado.motivoRechazo()).isEqualTo("Faltan fotos del baño");
	}

	// ─────────────────────────────────────────
	// Servicios adicionales — MA-55
	// ─────────────────────────────────────────

	@Test
	void otroUsuarioNoPuedeDefinirServiciosAdicionales() {
		existe(alojamiento);
		ServicioAdicionalDTO desayuno = new ServicioAdicionalDTO(null, "Desayuno", new BigDecimal("30000"),
				Modalidad.POR_NOCHE);

		assertThatThrownBy(() -> servicio.agregarServicioAdicional(otroAnfitrion, 10L, desayuno))
				.isInstanceOf(AccesoDenegadoException.class);
		assertThatThrownBy(() -> servicio.agregarServicioAdicional(administrador, 10L, desayuno))
				.isInstanceOf(AccesoDenegadoException.class);
	}
}
