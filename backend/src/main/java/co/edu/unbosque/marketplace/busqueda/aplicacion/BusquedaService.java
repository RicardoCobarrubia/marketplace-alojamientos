package co.edu.unbosque.marketplace.busqueda.aplicacion;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import co.edu.unbosque.marketplace.alojamientos.aplicacion.AlojamientoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.alojamientos.aplicacion.AlojamientoDTO;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.busqueda.infraestructura.FiltrosBusqueda;
import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.cotizacion.aplicacion.CotizacionService;
import co.edu.unbosque.marketplace.disponibilidad.dominio.RangoFechas;

/**
 * Búsqueda de alojamientos con filtros (MA-61). Todos los filtros se combinan en una sola
 * consulta paginada, así responde rápido aunque haya miles de alojamientos (escenario 3). Sin
 * ningún filtro devuelve todos los alojamientos publicados, de a una página.
 */
@Service
public class BusquedaService {

	static final int TAMANO_POR_DEFECTO = 20;
	static final String SIN_RESULTADOS = "No encontramos alojamientos con esos filtros. Prueba con otras fechas"
			+ " o quita algún filtro.";

	/** Del más barato al más caro; a igual precio, el más antiguo primero para que el orden sea estable. */
	private static final Sort ORDEN = Sort.by("precioNoche").ascending().and(Sort.by("id").ascending());

	private final AlojamientoService alojamientoService;
	private final CotizacionService cotizacion;
	private final Clock reloj;

	public BusquedaService(AlojamientoService alojamientoService, CotizacionService cotizacion, Clock reloj) {
		this.alojamientoService = alojamientoService;
		this.cotizacion = cotizacion;
		this.reloj = reloj;
	}

	@Transactional(readOnly = true)
	public ResultadoBusquedaDTO buscar(CriteriosBusquedaDTO criterios) {
		Specification<Alojamiento> filtro = Specification.allOf(filtros(criterios));
		int pagina = criterios.pagina() == null ? 0 : criterios.pagina();
		int tamano = criterios.tamano() == null ? TAMANO_POR_DEFECTO : criterios.tamano();
		Page<Alojamiento> encontrados = alojamientoService.buscarConFiltros(filtro,
				PageRequest.of(pagina, tamano, ORDEN));
		return new ResultadoBusquedaDTO(encontrados.map(AlojamientoDTO::desde).getContent(), pagina, tamano,
				encontrados.getTotalElements(), encontrados.getTotalPages(),
				encontrados.getTotalElements() == 0 ? SIN_RESULTADOS : null);
	}

	/** Un filtro por cada criterio que llegó (MA-61, escenario 1). */
	private List<Specification<Alojamiento>> filtros(CriteriosBusquedaDTO criterios) {
		List<Specification<Alojamiento>> filtros = new ArrayList<>();
		filtros.add(FiltrosBusqueda.activos());
		if (criterios.ciudadId() != null) {
			filtros.add(FiltrosBusqueda.enCiudad(criterios.ciudadId()));
		}
		if (criterios.llegada() != null || criterios.salida() != null) {
			// Las mismas reglas de fechas que al cotizar y reservar
			RangoFechas rango = cotizacion.validarFechas(criterios.llegada(), criterios.salida());
			filtros.add(FiltrosBusqueda.disponibleEntre(rango.llegada(), rango.salida(), reloj.instant()));
		}
		if (criterios.huespedes() != null) {
			filtros.add(FiltrosBusqueda.paraHuespedes(criterios.huespedes()));
		}
		if (criterios.tipoId() != null) {
			filtros.add(FiltrosBusqueda.deTipo(criterios.tipoId()));
		}
		if (criterios.precioMin() != null && criterios.precioMax() != null
				&& criterios.precioMin().compareTo(criterios.precioMax()) > 0) {
			throw new SolicitudInvalidaException("RANGO_PRECIO_INVALIDO",
					"El precio mínimo no puede ser mayor que el máximo.");
		}
		if (criterios.precioMin() != null) {
			filtros.add(FiltrosBusqueda.precioDesde(criterios.precioMin()));
		}
		if (criterios.precioMax() != null) {
			filtros.add(FiltrosBusqueda.precioHasta(criterios.precioMax()));
		}
		if (criterios.servicioIds() != null) {
			criterios.servicioIds().forEach(servicio -> filtros.add(FiltrosBusqueda.conServicio(servicio)));
		}
		return filtros;
	}
}
