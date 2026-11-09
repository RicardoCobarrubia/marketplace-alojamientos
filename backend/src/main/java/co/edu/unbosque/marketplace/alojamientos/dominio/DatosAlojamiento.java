package co.edu.unbosque.marketplace.alojamientos.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Información editable de un alojamiento, ya con los catálogos resueltos. La arma el servicio a
 * partir de la petición y se la pasa a {@link Alojamiento} para crear o editar.
 */
public record DatosAlojamiento(
		String titulo,
		String descripcion,
		String ubicacion,
		TipoAlojamiento tipo,
		Ciudad ciudad,
		PoliticaCancelacion politicaCancelacion,
		int capacidad,
		BigDecimal precioNoche,
		BigDecimal tarifaLimpieza,
		Set<Servicio> servicios,
		List<String> reglas,
		List<String> imagenes) {
}
