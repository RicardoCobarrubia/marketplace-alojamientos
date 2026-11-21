package co.edu.unbosque.marketplace.reportes.infraestructura;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplace.reservas.dominio.Reserva;

/**
 * Lo que va en el detalle del reporte (MA-85): lo creado en el rango {@code [desde, hasta)}. Las
 * fechas salen como texto en la zona horaria pedida, listas para mostrar.
 */
public interface ConsultasReporteRepository extends Repository<Reserva, Long> {

	interface FilaUsuario {
		Long getId();

		String getNombre();

		String getEmail();

		String getRol();

		String getEstado();

		String getCreadoEn();
	}

	interface FilaAlojamiento {
		Long getId();

		String getTitulo();

		String getCiudad();

		String getTipo();

		Long getAnfitrionId();

		String getEstado();

		BigDecimal getPrecioNoche();

		String getCreadoEn();
	}

	interface FilaReserva {
		Long getId();

		Long getAlojamientoId();

		String getCiudad();

		Long getHuespedId();

		String getLlegada();

		String getSalida();

		String getEstado();

		BigDecimal getTotal();

		BigDecimal getTarifaServicio();

		String getCreadoEn();
	}

	interface FilaDemanda {
		String getCiudad();

		String getDepartamento();

		String getTipo();

		long getReservas();

		BigDecimal getIngresos();
	}

	@Query(value = """
			SELECT id, nombre, email, rol, estado_cuenta AS estado,
			       to_char(creado_en AT TIME ZONE :zona, 'YYYY-MM-DD HH24:MI') AS "creadoEn"
			FROM usuario
			WHERE creado_en >= :desde AND creado_en < :hasta
			ORDER BY creado_en, id""", nativeQuery = true)
	List<FilaUsuario> usuarios(@Param("desde") Instant desde, @Param("hasta") Instant hasta,
			@Param("zona") String zona);

	@Query(value = """
			SELECT a.id, a.titulo, c.nombre AS ciudad, t.nombre AS tipo, a.anfitrion_id AS "anfitrionId", a.estado,
			       a.precio_noche AS "precioNoche",
			       to_char(a.creado_en AT TIME ZONE :zona, 'YYYY-MM-DD HH24:MI') AS "creadoEn"
			FROM alojamiento a
			JOIN ciudad c ON c.id = a.ciudad_id
			JOIN tipo_alojamiento t ON t.id = a.tipo_id
			WHERE a.creado_en >= :desde AND a.creado_en < :hasta
			ORDER BY a.creado_en, a.id""", nativeQuery = true)
	List<FilaAlojamiento> alojamientos(@Param("desde") Instant desde, @Param("hasta") Instant hasta,
			@Param("zona") String zona);

	@Query(value = """
			SELECT r.id, r.alojamiento_id AS "alojamientoId", c.nombre AS ciudad, r.huesped_id AS "huespedId",
			       to_char(r.fecha_llegada, 'YYYY-MM-DD') AS llegada, to_char(r.fecha_salida, 'YYYY-MM-DD') AS salida,
			       r.estado, r.total, r.tarifa_servicio AS "tarifaServicio",
			       to_char(r.creado_en AT TIME ZONE :zona, 'YYYY-MM-DD HH24:MI') AS "creadoEn"
			FROM reserva r
			JOIN alojamiento a ON a.id = r.alojamiento_id
			JOIN ciudad c ON c.id = a.ciudad_id
			WHERE r.creado_en >= :desde AND r.creado_en < :hasta
			ORDER BY r.creado_en, r.id""", nativeQuery = true)
	List<FilaReserva> reservas(@Param("desde") Instant desde, @Param("hasta") Instant hasta,
			@Param("zona") String zona);

	/**
	 * Reservas CONFIRMADA y COMPLETADA por ciudad y tipo de alojamiento, con sus ingresos
	 * estimados: las mismas que suma el panel (MA-84), así los totales coinciden.
	 */
	@Query(value = """
			SELECT c.nombre AS ciudad, c.departamento, t.nombre AS tipo, count(*) AS reservas,
			       sum(r.tarifa_servicio) AS ingresos
			FROM reserva r
			JOIN alojamiento a ON a.id = r.alojamiento_id
			JOIN ciudad c ON c.id = a.ciudad_id
			JOIN tipo_alojamiento t ON t.id = a.tipo_id
			WHERE r.estado IN ('CONFIRMADA', 'COMPLETADA') AND r.creado_en >= :desde AND r.creado_en < :hasta
			GROUP BY c.id, c.nombre, c.departamento, t.id, t.nombre
			ORDER BY count(*) DESC, c.nombre, t.nombre""", nativeQuery = true)
	List<FilaDemanda> demandaPorCiudadYTipo(@Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
