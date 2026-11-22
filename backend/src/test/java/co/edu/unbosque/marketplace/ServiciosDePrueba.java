package co.edu.unbosque.marketplace;

import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.edu.unbosque.marketplace.alojamientos.aplicacion.AlojamientoService;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.AlojamientoRepository;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.CiudadRepository;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.ServicioAdicionalRepository;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.ServicioRepository;
import co.edu.unbosque.marketplace.alojamientos.infraestructura.TipoAlojamientoRepository;
import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.disponibilidad.aplicacion.DisponibilidadService;
import co.edu.unbosque.marketplace.disponibilidad.infraestructura.PeriodoDisponibleRepository;
import co.edu.unbosque.marketplace.disponibilidad.infraestructura.PeriodoReservadoRepository;
import co.edu.unbosque.marketplace.notificaciones.aplicacion.PublicadorEventos;
import co.edu.unbosque.marketplace.reservas.aplicacion.EventosReserva;
import co.edu.unbosque.marketplace.reservas.aplicacion.GestionReservasService;
import co.edu.unbosque.marketplace.reservas.dominio.CalculadoraDevolucion;
import co.edu.unbosque.marketplace.reservas.dominio.DevolucionFlexible;
import co.edu.unbosque.marketplace.reservas.infraestructura.ReservaRepository;
import co.edu.unbosque.marketplace.usuarios.aplicacion.SesionesEnCache;
import co.edu.unbosque.marketplace.usuarios.aplicacion.UsuarioService;
import co.edu.unbosque.marketplace.usuarios.infraestructura.UsuarioRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Servicios reales de otros módulos para las pruebas unitarias. Cada uno recibe el repositorio
 * simulado que la prueba ya configura; lo demás va simulado porque esas pruebas solo usan los
 * métodos que los módulos ofrecen a los demás.
 */
public final class ServiciosDePrueba {

	private static final Clock RELOJ = Clock.fixed(TestDatos.AHORA, ZoneOffset.UTC);
	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

	private ServiciosDePrueba() {
	}

	public static UsuarioService usuarios(UsuarioRepository repositorio) {
		return new UsuarioService(repositorio, mock(PasswordEncoder.class), mock(AuthenticationManager.class),
				mock(Auditoria.class), mock(SesionesEnCache.class), RELOJ, 5, Duration.ofMinutes(15));
	}

	public static AlojamientoService alojamientos(AlojamientoRepository repositorio) {
		return new AlojamientoService(repositorio, mock(ServicioAdicionalRepository.class),
				usuarios(mock(UsuarioRepository.class)), mock(CiudadRepository.class),
				mock(TipoAlojamientoRepository.class), mock(ServicioRepository.class),
				mock(PoliticaCancelacionRepository.class), mock(PublicadorEventos.class), mock(Auditoria.class), RELOJ);
	}

	public static DisponibilidadService disponibilidad(PeriodoReservadoRepository reservados) {
		return new DisponibilidadService(alojamientos(mock(AlojamientoRepository.class)),
				mock(PeriodoDisponibleRepository.class), reservados, RELOJ, BOGOTA);
	}

	public static GestionReservasService reservas(ReservaRepository repositorio) {
		return new GestionReservasService(repositorio, disponibilidad(mock(PeriodoReservadoRepository.class)),
				mock(EventosReserva.class), new CalculadoraDevolucion(List.of(new DevolucionFlexible())), JsonMapper.builder().build(), RELOJ,
				BOGOTA);
	}
}
