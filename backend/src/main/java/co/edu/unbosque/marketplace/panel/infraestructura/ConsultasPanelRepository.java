package co.edu.unbosque.marketplace.panel.infraestructura;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplace.reservas.dominio.Reserva;

/**
 * Consultas de agregación del panel (MA-83, MA-84). Cuentan y suman en PostgreSQL: con 10.000
 * usuarios y 100.000 reservas no se trae ninguna fila a memoria (escenario 3 de MA-83).
 * <p>
 * Los ingresos y la demanda cuentan las reservas CONFIRMADA y COMPLETADA creadas en el rango
 * {@code [desde, hasta)}, como pide MA-84.
 */
public interface ConsultasPanelRepository extends Repository<Reserva, Long> {

	/** Cuántas filas hay de cada valor (un rol, un estado). */
	interface Conteo {
		String getClave();

		long getTotal();
	}

	interface Ingresos {
		BigDecimal getIngresos();

		long getReservas();
	}

	interface ReservasPorCiudad {
		Long getCiudadId();

		String getCiudad();

		String getDepartamento();

		long getReservas();
	}

	@Query(value = "SELECT rol AS clave, count(*) AS total FROM usuario GROUP BY rol", nativeQuery = true)
	List<Conteo> usuariosPorRol();

	@Query(value = "SELECT count(*) FROM alojamiento WHERE estado = 'ACTIVA'", nativeQuery = true)
	long alojamientosActivos();

	@Query(value = "SELECT estado AS clave, count(*) AS total FROM reserva GROUP BY estado", nativeQuery = true)
	List<Conteo> reservasPorEstado();

	/** Suma de las tarifas de servicio, que es lo que gana la plataforma (MA-84, escenario 1). */
	@Query(value = """
			SELECT coalesce(sum(tarifa_servicio), 0) AS ingresos, count(*) AS reservas
			FROM reserva
			WHERE estado IN ('CONFIRMADA', 'COMPLETADA') AND creado_en >= :desde AND creado_en < :hasta""",
			nativeQuery = true)
	Ingresos ingresos(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

	/** Las 5 ciudades con más reservas; a igual número, por nombre (MA-84, escenario 2). */
	@Query(value = """
			SELECT c.id AS "ciudadId", c.nombre AS ciudad, c.departamento AS departamento, count(*) AS reservas
			FROM reserva r
			JOIN alojamiento a ON a.id = r.alojamiento_id
			JOIN ciudad c ON c.id = a.ciudad_id
			WHERE r.estado IN ('CONFIRMADA', 'COMPLETADA') AND r.creado_en >= :desde AND r.creado_en < :hasta
			GROUP BY c.id, c.nombre, c.departamento
			ORDER BY count(*) DESC, c.nombre, c.id
			LIMIT 5""", nativeQuery = true)
	List<ReservasPorCiudad> ciudadesConMasReservas(@Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
