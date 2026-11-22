package co.edu.unbosque.marketplace.cotizacion.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import co.edu.unbosque.marketplace.alojamientos.aplicacion.AlojamientoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento.EstadoAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.ServicioAdicional;
import co.edu.unbosque.marketplace.comun.excepcion.RecursoNoEncontradoException;
import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.cotizacion.dominio.CalculadoraCotizacion;
import co.edu.unbosque.marketplace.cotizacion.dominio.ConfiguracionCotizacion;
import co.edu.unbosque.marketplace.cotizacion.dominio.Cotizacion;
import co.edu.unbosque.marketplace.cotizacion.infraestructura.ConfiguracionCotizacionRepository;
import co.edu.unbosque.marketplace.disponibilidad.dominio.RangoFechas;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

/**
 * Cotización de una estadía (MA-63, MA-64) y su configuración (MA-65).
 * <p>
 * La configuración se lee de la base de datos en cada cotización: un cambio del administrador
 * se aplica a la siguiente cotización, sin redespliegue (MA-65, escenario 1).
 */
@Service
public class CotizacionService {

	private static final Logger log = LoggerFactory.getLogger(CotizacionService.class);

	private final AlojamientoService alojamientoService;
	private final ConfiguracionCotizacionRepository configuracionRepository;
	private final CalculadoraCotizacion calculadora;
	private final Auditoria auditoria;
	private final Clock reloj;
	private final ZoneId zonaHoraria;

	public CotizacionService(AlojamientoService alojamientoService,
			ConfiguracionCotizacionRepository configuracionRepository, CalculadoraCotizacion calculadora,
			Auditoria auditoria, Clock reloj,
			@Value("${marketplace.zona-horaria}") ZoneId zonaHoraria) {
		this.alojamientoService = alojamientoService;
		this.configuracionRepository = configuracionRepository;
		this.calculadora = calculadora;
		this.auditoria = auditoria;
		this.reloj = reloj;
		this.zonaHoraria = zonaHoraria;
	}

	// ─────────────────────────────────────────
	// Cotizar — MA-63, MA-64
	// ─────────────────────────────────────────

	/**
	 * Cotiza [llegada, salida) con los servicios adicionales elegidos. {@code actor} puede ser
	 * nulo: la cotización es pública para los alojamientos ACTIVA.
	 */
	@Transactional(readOnly = true)
	public CotizacionDTO cotizar(Usuario actor, Long alojamientoId, LocalDate llegada, LocalDate salida,
			Set<Long> servicioIds) {
		RangoFechas rango = validarFechas(llegada, salida);
		Alojamiento alojamiento = alojamientoService.buscarPorId(alojamientoId)
				.filter(encontrado -> puedeVer(actor, encontrado))
				.orElseThrow(() -> new RecursoNoEncontradoException("El alojamiento no existe."));
		return CotizacionDTO.desde(alojamientoId, llegada, salida, calcular(alojamiento, rango, servicioIds));
	}

	/**
	 * Calcula la cotización de un alojamiento ya cargado. La usa la reserva (MA-66) dentro de su
	 * propia transacción, para guardar una copia del valor cobrado.
	 */
	public Cotizacion calcular(Alojamiento alojamiento, RangoFechas rango, Set<Long> servicioIds) {
		List<ServicioAdicional> servicios = elegirServicios(alojamiento, servicioIds);
		return calculadora.calcular(alojamiento, rango.noches(), servicios, configuracionActual());
	}

	/** La llegada no puede ser anterior a hoy y la salida debe ser posterior (MA-63, escenario 1). */
	public RangoFechas validarFechas(LocalDate llegada, LocalDate salida) {
		if (llegada == null || salida == null) {
			throw fechasInvalidas("Indica la fecha de llegada y la de salida.");
		}
		if (llegada.isBefore(LocalDate.ofInstant(reloj.instant(), zonaHoraria))) {
			throw fechasInvalidas("La llegada no puede ser anterior a hoy.");
		}
		if (!salida.isAfter(llegada)) {
			throw fechasInvalidas("La salida debe ser posterior a la llegada.");
		}
		return new RangoFechas(llegada, salida);
	}

	// ─────────────────────────────────────────
	// Configuración — MA-65
	// ─────────────────────────────────────────

	@Transactional(readOnly = true)
	public ConfiguracionCotizacionDTO obtenerConfiguracion() {
		return ConfiguracionCotizacionDTO.desde(configuracionActual());
	}

	@Transactional
	public ConfiguracionCotizacionDTO actualizarConfiguracion(Usuario administrador, ConfiguracionCotizacionDTO datos) {
		ConfiguracionCotizacion configuracion = configuracionRepository.buscarParaActualizar(ConfiguracionCotizacion.ID_UNICO)
				.orElseThrow(CotizacionService::sinConfiguracion);
		String anterior = resumen(ConfiguracionCotizacionDTO.desde(configuracion));
		configuracion.actualizar(datos.porcentajeTarifaServicio(), datos.porcentajeDescuentoEstadiaLarga(),
				datos.minimoNochesDescuento(), administrador.getId(), reloj.instant());
		configuracionRepository.saveAndFlush(configuracion);
		log.info("Configuración de cotización actualizada (administrador {})", administrador.getId()); // NOSONAR: solo un id numérico
		ConfiguracionCotizacionDTO actualizada = ConfiguracionCotizacionDTO.desde(configuracion);
		auditoria.registrar(EventoAuditoria.accion("ACTUALIZAR_CONFIGURACION_COTIZACION", "CONFIGURACION_COTIZACION",
				(long) ConfiguracionCotizacion.ID_UNICO, EventoAuditoria.Resultado.EXITO,
				"Antes: " + anterior + ". Ahora: " + resumen(actualizada) + "."));
		return actualizada;
	}

	// ─────────────────────────────────────────
	// Auxiliares
	// ─────────────────────────────────────────

	private static String resumen(ConfiguracionCotizacionDTO configuracion) {
		return "tarifa " + configuracion.porcentajeTarifaServicio() + " %, descuento "
				+ configuracion.porcentajeDescuentoEstadiaLarga() + " % desde " + configuracion.minimoNochesDescuento()
				+ " noches";
	}

	private ConfiguracionCotizacion configuracionActual() {
		return configuracionRepository.findById(ConfiguracionCotizacion.ID_UNICO)
				.orElseThrow(CotizacionService::sinConfiguracion);
	}

	private static IllegalStateException sinConfiguracion() {
		return new IllegalStateException("Falta la configuración de la cotización (migración V5).");
	}

	/** Solo servicios activos del mismo alojamiento (MA-64, escenario 3). */
	private static List<ServicioAdicional> elegirServicios(Alojamiento alojamiento, Set<Long> servicioIds) {
		if (servicioIds == null || servicioIds.isEmpty()) {
			return List.of();
		}
		Map<Long, ServicioAdicional> disponibles = alojamiento.getServiciosAdicionales().stream()
				.filter(ServicioAdicional::isActivo)
				.collect(Collectors.toMap(ServicioAdicional::getId, Function.identity()));
		return servicioIds.stream()
				.map(id -> {
					ServicioAdicional servicio = disponibles.get(id);
					if (servicio == null) {
						throw new SolicitudInvalidaException("SERVICIO_NO_PERTENECE",
								"El servicio adicional " + id + " no pertenece a este alojamiento.");
					}
					return servicio;
				})
				.toList();
	}

	/** Igual que el detalle: los no ACTIVA solo los ven el dueño y el administrador. */
	private static boolean puedeVer(Usuario actor, Alojamiento alojamiento) {
		return alojamiento.getEstado() == EstadoAlojamiento.ACTIVA
				|| (actor != null && (actor.getRol() == Rol.ADMINISTRADOR || alojamiento.esDe(actor.getId())));
	}

	private static SolicitudInvalidaException fechasInvalidas(String mensaje) {
		return new SolicitudInvalidaException("FECHAS_INVALIDAS", mensaje);
	}
}
