package co.edu.unbosque.marketplace.alojamientos.aplicacion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento.EstadoAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.ImagenAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.ReglaAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.Servicio;
import co.edu.unbosque.marketplace.alojamientos.dominio.ServicioAdicional;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * DTO de alojamiento: se usa para registrar, editar, rechazar y responder (MA-53 a MA-57).
 * <p>
 * Los campos que solo debe poner el sistema ({@code id}, {@code anfitrionId}, {@code estado},
 * fechas...) son de solo lectura: si el cliente los envía se ignoran. Así nadie puede crear un
 * alojamiento ya ACTIVA ni a nombre de otro anfitrión.
 * <p>
 * Para ciudad, tipo, política y servicios el cliente envía los ids ({@code ciudadId}...) y la
 * respuesta trae además los objetos con su nombre ({@code ciudad}...).
 */
public record AlojamientoDTO(

		@JsonProperty(access = Access.READ_ONLY)
		Long id,

		@JsonProperty(access = Access.READ_ONLY)
		Long anfitrionId,

		@JsonProperty(access = Access.READ_ONLY)
		String anfitrionNombre,

		@NotBlank(groups = Datos.class, message = "El título es obligatorio.")
		@Size(groups = Datos.class, max = 120, message = "El título admite máximo 120 caracteres.")
		String titulo,

		@NotBlank(groups = Datos.class, message = "La descripción es obligatoria.")
		@Size(groups = Datos.class, max = 2000, message = "La descripción admite máximo 2000 caracteres.")
		String descripcion,

		@NotBlank(groups = Datos.class, message = "La ubicación es obligatoria.")
		@Size(groups = Datos.class, max = 200, message = "La ubicación admite máximo 200 caracteres.")
		String ubicacion,

		@NotNull(groups = Datos.class, message = "La ciudad es obligatoria.")
		Long ciudadId,

		@JsonProperty(access = Access.READ_ONLY)
		CiudadDTO ciudad,

		@NotNull(groups = Datos.class, message = "El tipo de alojamiento es obligatorio.")
		Short tipoId,

		@JsonProperty(access = Access.READ_ONLY)
		TipoAlojamientoDTO tipo,

		@NotNull(groups = Datos.class, message = "La política de cancelación es obligatoria.")
		Short politicaCancelacionId,

		@JsonProperty(access = Access.READ_ONLY)
		PoliticaCancelacionDTO politicaCancelacion,

		@NotNull(groups = Datos.class, message = "La capacidad es obligatoria.")
		@Positive(groups = Datos.class, message = "La capacidad debe ser mayor que cero.")
		Integer capacidad,

		@NotNull(groups = Datos.class, message = "El precio por noche es obligatorio.")
		@Positive(groups = Datos.class, message = "El precio por noche debe ser positivo.")
		@Digits(groups = Datos.class, integer = 10, fraction = 2, message = "El precio admite máximo 2 decimales.")
		BigDecimal precioNoche,

		@NotNull(groups = Datos.class, message = "La tarifa de limpieza es obligatoria (puede ser 0).")
		@PositiveOrZero(groups = Datos.class, message = "La tarifa de limpieza no puede ser negativa.")
		@Digits(groups = Datos.class, integer = 10, fraction = 2, message = "La tarifa admite máximo 2 decimales.")
		BigDecimal tarifaLimpieza,

		@NotEmpty(groups = Datos.class, message = "Elige al menos un servicio.")
		Set<@NotNull(groups = Datos.class) Short> servicioIds,

		@JsonProperty(access = Access.READ_ONLY)
		List<ServicioDTO> servicios,

		@NotEmpty(groups = Datos.class, message = "Agrega al menos una regla.")
		@Size(groups = Datos.class, max = 20, message = "Máximo 20 reglas.")
		List<@NotBlank(groups = Datos.class, message = "Una regla está vacía.")
				@Size(groups = Datos.class, max = 300) String> reglas,

		/* La primera imagen es la principal. */
		@NotNull(groups = Datos.class, message = "Agrega entre 1 y 10 imágenes.")
		@Size(groups = Datos.class, min = 1, max = 10, message = "Agrega entre 1 y 10 imágenes.")
		List<@NotBlank(groups = Datos.class, message = "Una imagen está vacía.")
				@Size(groups = Datos.class, max = 500) String> imagenes,

		@JsonProperty(access = Access.READ_ONLY)
		List<ServicioAdicionalDTO> serviciosAdicionales,

		@JsonProperty(access = Access.READ_ONLY)
		EstadoAlojamiento estado,

		/* Lo envía el administrador al rechazar (MA-57); el anfitrión lo ve en la respuesta. */
		@NotBlank(groups = Rechazo.class, message = "El motivo es obligatorio.")
		@Size(groups = Rechazo.class, max = 500, message = "El motivo admite máximo 500 caracteres.")
		String motivoRechazo,

		@JsonProperty(access = Access.READ_ONLY)
		Instant creadoEn,

		@JsonProperty(access = Access.READ_ONLY)
		Instant actualizadoEn) {

	/** Validaciones al registrar o editar (MA-53, MA-54). */
	public interface Datos {
	}

	/** Validaciones al rechazar: solo el motivo (MA-57). */
	public interface Rechazo {
	}

	/** Convierte la entidad en DTO para la respuesta. */
	public static AlojamientoDTO desde(Alojamiento alojamiento) {
		List<ServicioDTO> servicios = alojamiento.getServicios().stream()
				.map(ServicioDTO::desde)
				.sorted(Comparator.comparing(ServicioDTO::nombre))
				.toList();
		return new AlojamientoDTO(
				alojamiento.getId(),
				alojamiento.getAnfitrion().getId(),
				alojamiento.getAnfitrion().getNombre(),
				alojamiento.getTitulo(),
				alojamiento.getDescripcion(),
				alojamiento.getUbicacion(),
				alojamiento.getCiudad().getId(),
				CiudadDTO.desde(alojamiento.getCiudad()),
				alojamiento.getTipo().getId(),
				TipoAlojamientoDTO.desde(alojamiento.getTipo()),
				alojamiento.getPoliticaCancelacion().getId(),
				PoliticaCancelacionDTO.desde(alojamiento.getPoliticaCancelacion()),
				alojamiento.getCapacidad(),
				alojamiento.getPrecioNoche(),
				alojamiento.getTarifaLimpieza(),
				alojamiento.getServicios().stream().map(Servicio::getId).collect(Collectors.toSet()),
				servicios,
				alojamiento.getReglas().stream().map(ReglaAlojamiento::getDescripcion).toList(),
				alojamiento.getImagenes().stream().map(ImagenAlojamiento::getRuta).toList(),
				alojamiento.getServiciosAdicionales().stream()
						.filter(ServicioAdicional::isActivo)
						.map(ServicioAdicionalDTO::desde)
						.toList(),
				alojamiento.getEstado(),
				alojamiento.getMotivoRechazo(),
				alojamiento.getCreadoEn(),
				alojamiento.getActualizadoEn());
	}
}
