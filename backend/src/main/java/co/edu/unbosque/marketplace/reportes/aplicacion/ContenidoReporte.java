package co.edu.unbosque.marketplace.reportes.aplicacion;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.panel.aplicacion.IndicadoresDTO;
import co.edu.unbosque.marketplace.panel.aplicacion.IngresosDTO;
import co.edu.unbosque.marketplace.panel.aplicacion.PanelService;
import co.edu.unbosque.marketplace.panel.aplicacion.Periodo;
import co.edu.unbosque.marketplace.reportes.dominio.Reporte;
import co.edu.unbosque.marketplace.reportes.dominio.Reporte.Seccion;
import co.edu.unbosque.marketplace.reportes.infraestructura.ConsultasReporteRepository;

/**
 * Arma el contenido del reporte de gestión (MA-85, escenario 1). Los totales salen del mismo
 * servicio que el panel, así el reporte y el panel coinciden (escenario 2). Las secciones se leen
 * de una misma foto de la base de datos.
 */
@Service
public class ContenidoReporte {

	private static final String RESERVAS = "Reservas";
	private static final String ESTADO = "Estado";
	private static final String CIUDAD = "Ciudad";
	private static final DateTimeFormatter FECHA_Y_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final PanelService panel;
	private final ConsultasReporteRepository consultas;
	private final Clock reloj;
	private final ZoneId zonaHoraria;

	public ContenidoReporte(PanelService panel, ConsultasReporteRepository consultas, Clock reloj,
			@Value("${marketplace.zona-horaria}") ZoneId zonaHoraria) {
		this.panel = panel;
		this.consultas = consultas;
		this.reloj = reloj;
		this.zonaHoraria = zonaHoraria;
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public Reporte armar(Periodo periodo, String generadoPor) {
		IndicadoresDTO indicadores = panel.indicadores();
		IngresosDTO ingresos = panel.ingresos(periodo.desde(), periodo.hasta());
		String zona = zonaHoraria.getId();
		List<String> encabezado = List.of(
				"Período: del " + periodo.desde() + " al " + periodo.hasta() + " (hora de Colombia)",
				"Generado por: " + generadoPor,
				"Generado el: " + FECHA_Y_HORA.format(reloj.instant().atZone(zonaHoraria)));
		return new Reporte("Reporte de gestión - Inversiones LR", encabezado, List.of(
				resumen(indicadores, ingresos),
				usuarios(periodo, zona),
				alojamientos(periodo, zona),
				reservas(periodo, zona),
				demanda(periodo)));
	}

	/** Las mismas cifras que el panel (MA-83, MA-84). */
	private static Seccion resumen(IndicadoresDTO indicadores, IngresosDTO ingresos) {
		List<List<Object>> filas = new ArrayList<>();
		filas.add(fila("Usuarios", indicadores.usuarios()));
		agregar(filas, "Usuarios ", indicadores.usuariosPorRol());
		filas.add(fila("Alojamientos activos", indicadores.alojamientosActivos()));
		filas.add(fila(RESERVAS, indicadores.reservas()));
		agregar(filas, RESERVAS + " ", indicadores.reservasPorEstado());
		filas.add(fila("Ingresos estimados del período", ingresos.ingresosEstimados()));
		filas.add(fila("Reservas confirmadas y completadas del período", ingresos.reservas()));
		return new Seccion("Resumen", List.of("Indicador", "Valor"), filas);
	}

	private Seccion usuarios(Periodo periodo, String zona) {
		List<List<Object>> filas = consultas.usuarios(periodo.inicio(zonaHoraria), periodo.fin(zonaHoraria), zona)
				.stream()
				.map(u -> fila(u.getId(), u.getNombre(), u.getEmail(), u.getRol(), u.getEstado(), u.getCreadoEn()))
				.toList();
		return new Seccion("Usuarios registrados", List.of("Id", "Nombre", "Correo", "Rol", ESTADO, "Registrado"),
				filas);
	}

	private Seccion alojamientos(Periodo periodo, String zona) {
		List<List<Object>> filas = consultas.alojamientos(periodo.inicio(zonaHoraria), periodo.fin(zonaHoraria), zona)
				.stream()
				.map(a -> fila(a.getId(), a.getTitulo(), a.getCiudad(), a.getTipo(), a.getAnfitrionId(), a.getEstado(),
						a.getPrecioNoche(), a.getCreadoEn()))
				.toList();
		return new Seccion("Alojamientos creados", List.of("Id", "Título", CIUDAD, "Tipo", "Anfitrión",
				ESTADO, "Precio por noche", "Creado"), filas);
	}

	private Seccion reservas(Periodo periodo, String zona) {
		List<List<Object>> filas = consultas.reservas(periodo.inicio(zonaHoraria), periodo.fin(zonaHoraria), zona)
				.stream()
				.map(r -> fila(r.getId(), r.getAlojamientoId(), r.getCiudad(), r.getHuespedId(), r.getLlegada(),
						r.getSalida(), r.getEstado(), r.getTotal(), r.getTarifaServicio(), r.getCreadoEn()))
				.toList();
		return new Seccion(RESERVAS, List.of("Id", "Alojamiento", CIUDAD, "Huésped", "Llegada", "Salida",
				ESTADO, "Total", "Tarifa de servicio", "Creada"), filas);
	}

	/** Las reservas que suman ingresos, por ciudad y tipo de alojamiento. */
	private Seccion demanda(Periodo periodo) {
		List<List<Object>> filas = consultas.demandaPorCiudadYTipo(periodo.inicio(zonaHoraria),
				periodo.fin(zonaHoraria))
				.stream()
				.map(d -> fila(d.getCiudad(), d.getDepartamento(), d.getTipo(), d.getReservas(), d.getIngresos()))
				.toList();
		return new Seccion("Demanda por ciudad y tipo", List.of(CIUDAD, "Departamento", "Tipo", RESERVAS,
				"Ingresos estimados"), filas);
	}

	private static void agregar(List<List<Object>> filas, String prefijo, Map<String, Long> conteos) {
		conteos.forEach((clave, total) -> filas.add(fila(prefijo + clave, total)));
	}

	/** Una fila que admite valores nulos (por ejemplo, un dato opcional vacío). */
	private static List<Object> fila(Object... valores) {
		return Arrays.asList(valores);
	}
}
