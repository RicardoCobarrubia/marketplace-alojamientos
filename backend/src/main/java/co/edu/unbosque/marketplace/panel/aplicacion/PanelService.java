package co.edu.unbosque.marketplace.panel.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.panel.infraestructura.ConsultasPanelRepository;
import co.edu.unbosque.marketplace.panel.infraestructura.ConsultasPanelRepository.Conteo;
import co.edu.unbosque.marketplace.panel.infraestructura.ConsultasPanelRepository.Ingresos;
import co.edu.unbosque.marketplace.reservas.dominio.EstadoReserva;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

/**
 * Panel del administrador (MA-83, MA-84). Cada consulta lee todas sus cifras de una misma foto de
 * la base de datos (REPEATABLE READ): una reserva que cambia de estado mientras se arma el panel
 * no queda contada dos veces ni falta en los totales.
 */
@Service
public class PanelService {

	/** Rango por defecto: los últimos 30 días, hoy incluido (MA-84, escenario 1). */
	static final int DIAS_POR_DEFECTO = 30;

	private final ConsultasPanelRepository consultas;
	private final Clock reloj;
	private final ZoneId zonaHoraria;

	public PanelService(ConsultasPanelRepository consultas, Clock reloj,
			@Value("${marketplace.zona-horaria}") ZoneId zonaHoraria) {
		this.consultas = consultas;
		this.reloj = reloj;
		this.zonaHoraria = zonaHoraria;
	}

	/** Usuarios, alojamientos activos y reservas por estado (MA-83, escenario 1). */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public IndicadoresDTO indicadores() {
		Map<String, Long> porRol = conCeros(Rol.values(), consultas.usuariosPorRol());
		Map<String, Long> porEstado = conCeros(EstadoReserva.values(), consultas.reservasPorEstado());
		return new IndicadoresDTO(suma(porRol), porRol, consultas.alojamientosActivos(), suma(porEstado), porEstado,
				reloj.instant());
	}

	/**
	 * Ingresos estimados y las 5 ciudades con más reservas en el rango (MA-84). Sin fechas, los
	 * últimos 30 días; con solo una, el rango de 30 días que empieza o termina en ella.
	 */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public IngresosDTO ingresos(LocalDate desde, LocalDate hasta) {
		Periodo periodo = periodo(desde, hasta);
		Instant inicio = periodo.inicio(zonaHoraria);
		Instant fin = periodo.fin(zonaHoraria);

		Ingresos ingresos = consultas.ingresos(inicio, fin);
		List<IngresosDTO.CiudadReservas> ciudades = consultas.ciudadesConMasReservas(inicio, fin)
				.stream()
				.map(ciudad -> new IngresosDTO.CiudadReservas(ciudad.getCiudadId(), ciudad.getCiudad(),
						ciudad.getDepartamento(), ciudad.getReservas()))
				.toList();
		return new IngresosDTO(periodo.desde(), periodo.hasta(), ingresos.getIngresos(), ingresos.getReservas(),
				ciudades);
	}

	/**
	 * El rango pedido, con los valores por defecto de MA-84: sin fechas, los últimos 30 días hasta
	 * hoy en la hora de Colombia; con solo una, los 30 días que empiezan o terminan en ella.
	 */
	public Periodo periodo(LocalDate desde, LocalDate hasta) {
		LocalDate fin = hasta;
		if (fin == null) {
			fin = desde != null ? desde.plusDays(DIAS_POR_DEFECTO - 1L) : LocalDate.now(reloj.withZone(zonaHoraria));
		}
		LocalDate inicio = desde != null ? desde : fin.minusDays(DIAS_POR_DEFECTO - 1L);
		if (inicio.isAfter(fin)) {
			throw new SolicitudInvalidaException("RANGO_INVALIDO",
					"La fecha inicial no puede ser posterior a la final.");
		}
		return new Periodo(inicio, fin);
	}

	private static Map<String, Long> conCeros(Enum<?>[] claves, List<Conteo> conteos) {
		Map<String, Long> resultado = new LinkedHashMap<>();
		for (Enum<?> clave : claves) {
			resultado.put(clave.name(), 0L);
		}
		for (Conteo conteo : conteos) {
			resultado.put(conteo.getClave(), conteo.getTotal());
		}
		return resultado;
	}

	private static long suma(Map<String, Long> conteos) {
		return conteos.values().stream().mapToLong(Long::longValue).sum();
	}
}
