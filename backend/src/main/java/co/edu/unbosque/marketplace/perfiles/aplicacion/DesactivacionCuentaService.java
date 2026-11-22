package co.edu.unbosque.marketplace.perfiles.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

import co.edu.unbosque.marketplace.reservas.aplicacion.GestionReservasService;
import co.edu.unbosque.marketplace.usuarios.aplicacion.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.alojamientos.aplicacion.AlojamientoService;
import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.comun.excepcion.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.comun.excepcion.ReglaNegocioException;
import co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva;
import co.edu.unbosque.marketplace.usuarios.aplicacion.SesionesEnCache;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.EstadoCuenta;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

/**
 * El huésped o el anfitrión desactiva su cuenta (MA-52, escenarios 1 y 2). La cuenta, sus
 * alojamientos pausados y el registro de auditoría cambian en una sola transacción.
 */
@Service
public class DesactivacionCuentaService {

	private static final Logger log = LoggerFactory.getLogger(DesactivacionCuentaService.class);

	/**
	 * Reservas que impiden desactivar mientras la estadía no termina: las confirmadas, las que
	 * están en revisión (siguen pagadas) y las que se están pagando (pueden confirmarse).
	 */
	private static final Set<EstadoReserva> COMPROMETIDAS = EnumSet.of(EstadoReserva.PENDIENTE_PAGO,
			EstadoReserva.CONFIRMADA, EstadoReserva.EN_REVISION);

	private final UsuarioService usuarioService;
	private final GestionReservasService gestionReservas;
	private final AlojamientoService alojamientoService;
	private final SesionesEnCache sesiones;
	private final Auditoria auditoria;
	private final Clock reloj;
	private final ZoneId zonaHoraria;

	public DesactivacionCuentaService(UsuarioService usuarioService, GestionReservasService gestionReservas,
			AlojamientoService alojamientoService, SesionesEnCache sesiones, Auditoria auditoria, Clock reloj,
			@Value("${marketplace.zona-horaria}") ZoneId zonaHoraria) {
		this.usuarioService = usuarioService;
		this.gestionReservas = gestionReservas;
		this.alojamientoService = alojamientoService;
		this.sesiones = sesiones;
		this.auditoria = auditoria;
		this.reloj = reloj;
		this.zonaHoraria = zonaHoraria;
	}

	/**
	 * Desactiva la cuenta: sus tokens dejan de valer en esta réplica de inmediato y en las demás
	 * en máximo 60 s (ADR-010), y sus alojamientos activos pasan a PAUSADA. Se rechaza si tiene
	 * reservas confirmadas que no han terminado, como huésped o en sus alojamientos.
	 * <p>
	 * Orden de los bloqueos: primero la cuenta y después los alojamientos, el mismo de la
	 * restricción (MA-81) y de una reserva nueva. Una reserva que empieza durante la desactivación
	 * espera y encuentra la cuenta desactivada o el alojamiento pausado; una que ya empezó termina
	 * antes y aquí se cuenta.
	 */
	@Transactional
	public void desactivar(Long usuarioId) {
		Usuario usuario = usuarioService.buscarParaActualizar(usuarioId)
				.orElseThrow(() -> new RecursoNoEncontradoException("El usuario no existe."));
		if (usuario.getRol() == Rol.ADMINISTRADOR) {
			throw new ReglaNegocioException("NO_SE_PUEDE_DESACTIVAR",
					"Un administrador no puede desactivar su cuenta.");
		}
		usuario.desactivar();
		int pausados = alojamientoService.pausarTodosDelAnfitrion(usuarioId, "Cuenta desactivada por su dueño");
		LocalDate hoy = LocalDate.ofInstant(reloj.instant(), zonaHoraria);
		if (gestionReservas.contarSinTerminarDelUsuario(usuarioId, COMPROMETIDAS, hoy) > 0) {
			// La transacción se deshace: la cuenta sigue activa y los alojamientos como estaban
			throw new ReglaNegocioException("RESERVAS_VIGENTES", "Tienes reservas confirmadas que aún no terminan."
					+ " Podrás desactivar la cuenta cuando terminen o se cancelen.");
		}
		auditoria.registrar(EventoAuditoria.cambio("DESACTIVAR_CUENTA", "USUARIO", usuarioId, EstadoCuenta.ACTIVA,
				EstadoCuenta.DESACTIVADA, pausados > 0 ? pausados + " alojamientos pausados" : null)
				.conActor(usuarioId, usuario.getRol()));
		sesiones.olvidarAlConfirmar(usuario.getEmail());
		log.info("Cuenta desactivada (usuario {}); {} alojamientos pausados", usuario.getId(), pausados);
	}
}
