package co.edu.unbosque.marketplace.resenas.aplicacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import co.edu.unbosque.marketplace.alojamientos.aplicacion.AlojamientoService;
import co.edu.unbosque.marketplace.reservas.aplicacion.GestionReservasService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento.EstadoAlojamiento;
import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.comun.excepcion.AccesoDenegadoException;
import co.edu.unbosque.marketplace.comun.excepcion.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.comun.excepcion.ReglaNegocioException;
import co.edu.unbosque.marketplace.notificaciones.aplicacion.PublicadorEventos;
import co.edu.unbosque.marketplace.resenas.dominio.Resena;
import co.edu.unbosque.marketplace.resenas.infraestructura.ResenaRepository;
import co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva;
import co.edu.unbosque.marketplace.reservas.dominio.Reserva;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;

/**
 * Reseñas (MA-77 a MA-79). Publicarla, consultarla y moderarla quedan en la auditoría (MA-87) y
 * la moderación se notifica al huésped (MA-75, escenario 2).
 */
@Service
public class ResenaService {

	/** Días después de la salida en que todavía se puede reseñar (MA-77, escenario 1). */
	static final int DIAS_PARA_RESENAR = 30;
	private static final String RESENA = "RESENA";

	private final ResenaRepository resenas;
	private final GestionReservasService gestionReservas;
	private final AlojamientoService alojamientoService;
	private final PublicadorEventos publicador;
	private final Auditoria auditoria;
	private final Clock reloj;
	private final ZoneId zonaHoraria;

	public ResenaService(ResenaRepository resenas, GestionReservasService gestionReservas,
			AlojamientoService alojamientoService, PublicadorEventos publicador, Auditoria auditoria, Clock reloj,
			@Value("${marketplace.zona-horaria}") ZoneId zonaHoraria) {
		this.resenas = resenas;
		this.gestionReservas = gestionReservas;
		this.alojamientoService = alojamientoService;
		this.publicador = publicador;
		this.auditoria = auditoria;
		this.reloj = reloj;
		this.zonaHoraria = zonaHoraria;
	}

	// ─────────────────────────────────────────
	// Publicar — MA-77, MA-78
	// ─────────────────────────────────────────

	/**
	 * El huésped reseña su reserva COMPLETADA dentro de los 30 días siguientes a la salida. Una
	 * reserva ajena no existe para él; una sola reseña por reserva.
	 */
	@Transactional
	public ResenaDTO publicar(Usuario usuario, Long reservaId, int calificacion, String comentario) {
		Reserva reserva = gestionReservas.buscarPorId(reservaId)
				.filter(encontrada -> encontrada.esVisiblePara(usuario))
				.orElseThrow(() -> new RecursoNoEncontradoException("La reserva no existe."));
		if (!reserva.esDe(usuario.getId())) {
			throw new AccesoDenegadoException("Solo el huésped de la reserva puede reseñarla.");
		}
		if (reserva.getEstado() != EstadoReserva.COMPLETADA) {
			throw new ReglaNegocioException("RESENA_NO_PERMITIDA", "Solo se puede reseñar una estadía completada.");
		}
		if (hoy().isAfter(reserva.getFechaSalida().plusDays(DIAS_PARA_RESENAR))) {
			throw new ReglaNegocioException("PLAZO_VENCIDO",
					"El plazo para reseñar (30 días después de la salida) ya terminó.");
		}
		if (resenas.existsByReservaId(reservaId)) {
			throw duplicada();
		}
		Resena resena = new Resena(reservaId, reserva.getAlojamiento().getId(), usuario.getId(), calificacion,
				comentario, reloj.instant());
		try {
			resena = resenas.saveAndFlush(resena);
		} catch (DataIntegrityViolationException e) {
			// Dos envíos simultáneos de la misma reserva: la restricción UNIQUE gana
			throw duplicada();
		}
		auditoria.registrar(EventoAuditoria.cambio("PUBLICAR_RESENA", RESENA, resena.getId(), null,
				resena.visibilidad(), "Calificación " + calificacion));
		Map<String, Object> datos = datosDelEvento(resena, reserva.getAlojamiento());
		datos.put("calificacion", calificacion);
		publicador.publicar("RESENA_PUBLICADA", RESENA, resena.getId(), datos);
		return ResenaDTO.desde(resena);
	}

	// ─────────────────────────────────────────
	// Consultar — MA-78, escenario 3
	// ─────────────────────────────────────────

	/** Las visibles de un alojamiento publicado, con su promedio. Las ocultas no cuentan. */
	@Transactional(readOnly = true)
	public ResenasAlojamientoDTO delAlojamiento(Long alojamientoId) {
		alojamientoService.buscarPorId(alojamientoId)
				.filter(alojamiento -> alojamiento.getEstado() == EstadoAlojamiento.ACTIVA)
				.orElseThrow(() -> new RecursoNoEncontradoException("El alojamiento no existe."));
		List<Resena> visibles = resenas.findByAlojamientoIdAndOcultaFalseOrderByCreadaEnDescIdDesc(alojamientoId);
		BigDecimal promedio = visibles.isEmpty() ? null
				: BigDecimal.valueOf(visibles.stream().mapToInt(Resena::getCalificacion).sum())
						.divide(BigDecimal.valueOf(visibles.size()), 1, RoundingMode.HALF_UP);
		return new ResenasAlojamientoDTO(alojamientoId, promedio, visibles.size(),
				visibles.stream().map(ResenaDTO::publica).toList());
	}

	// ─────────────────────────────────────────
	// Moderar — MA-79
	// ─────────────────────────────────────────

	/** Para el administrador: todas, o solo las ocultas o solo las visibles. */
	@Transactional(readOnly = true)
	public List<ResenaDTO> listar(Boolean oculta) {
		List<Resena> encontradas = oculta == null ? resenas.findAllByOrderByCreadaEnDescIdDesc()
				: resenas.findByOcultaOrderByCreadaEnDescIdDesc(oculta);
		return encontradas.stream().map(ResenaDTO::desde).toList();
	}

	@Transactional
	public ResenaDTO ocultar(Long resenaId, String motivo) {
		Resena resena = paraModerar(resenaId);
		resena.ocultar(motivo, reloj.instant());
		moderada(resena, "OCULTAR_RESENA", "RESENA_OCULTA", "VISIBLE", resena.getMotivoOcultamiento());
		return ResenaDTO.desde(resena);
	}

	@Transactional
	public ResenaDTO mostrar(Long resenaId) {
		Resena resena = paraModerar(resenaId);
		resena.mostrar(reloj.instant());
		moderada(resena, "MOSTRAR_RESENA", "RESENA_VISIBLE", "OCULTA", null);
		return ResenaDTO.desde(resena);
	}

	/** MA-79, escenario 3: queda en la auditoría y se notifica al huésped. */
	private void moderada(Resena resena, String accion, String evento, String anterior, String motivo) {
		auditoria.registrar(EventoAuditoria.cambio(accion, RESENA, resena.getId(), anterior, resena.visibilidad(),
				motivo));
		Alojamiento alojamiento = alojamientoService.buscarPorId(resena.getAlojamientoId())
				.orElseThrow(() -> new IllegalStateException("El alojamiento de la reseña no existe."));
		Map<String, Object> datos = datosDelEvento(resena, alojamiento);
		if (motivo != null) {
			datos.put("motivo", motivo);
		}
		publicador.publicar(evento, RESENA, resena.getId(), datos);
	}

	private Resena paraModerar(Long resenaId) {
		return resenas.buscarParaModerar(resenaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("La reseña no existe."));
	}

	private static Map<String, Object> datosDelEvento(Resena resena, Alojamiento alojamiento) {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("resenaId", resena.getId());
		datos.put("huespedId", resena.getHuespedId());
		datos.put("alojamientoId", alojamiento.getId());
		datos.put("anfitrionId", alojamiento.getAnfitrion().getId());
		datos.put("titulo", alojamiento.getTitulo());
		return datos;
	}

	private static ReglaNegocioException duplicada() {
		return new ReglaNegocioException("RESENA_DUPLICADA", "Esta reserva ya tiene una reseña.");
	}

	private LocalDate hoy() {
		return LocalDate.ofInstant(reloj.instant(), zonaHoraria);
	}
}
