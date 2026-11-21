package co.edu.unbosque.marketplace.busqueda.infraestructura;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.jpa.domain.Specification;

import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.Alojamiento.EstadoAlojamiento;
import co.edu.unbosque.marketplace.alojamientos.dominio.Servicio;
import co.edu.unbosque.marketplace.disponibilidad.dominio.PeriodoDisponible;
import co.edu.unbosque.marketplace.disponibilidad.dominio.PeriodoReservado;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Filtros de la búsqueda de alojamientos (MA-61). Cada uno es una condición que PostgreSQL evalúa
 * en la misma consulta: no se trae ningún alojamiento a memoria para descartarlo después.
 */
public final class FiltrosBusqueda {

	private static final String ALOJAMIENTO = "alojamiento";

	private FiltrosBusqueda() {
	}

	/** Solo los publicados (MA-61, escenario 2): un huésped nunca ve los demás estados. */
	public static Specification<Alojamiento> activos() {
		return (raiz, consulta, cb) -> cb.equal(raiz.get("estado"), EstadoAlojamiento.ACTIVA);
	}

	public static Specification<Alojamiento> enCiudad(Long ciudadId) {
		return (raiz, consulta, cb) -> cb.equal(raiz.get("ciudad").get("id"), ciudadId);
	}

	public static Specification<Alojamiento> paraHuespedes(int huespedes) {
		return (raiz, consulta, cb) -> cb.greaterThanOrEqualTo(raiz.get("capacidad"), huespedes);
	}

	public static Specification<Alojamiento> deTipo(Short tipoId) {
		return (raiz, consulta, cb) -> cb.equal(raiz.get("tipo").get("id"), tipoId);
	}

	public static Specification<Alojamiento> precioDesde(BigDecimal minimo) {
		return (raiz, consulta, cb) -> cb.greaterThanOrEqualTo(raiz.get("precioNoche"), minimo);
	}

	public static Specification<Alojamiento> precioHasta(BigDecimal maximo) {
		return (raiz, consulta, cb) -> cb.lessThanOrEqualTo(raiz.get("precioNoche"), maximo);
	}

	/** Que ofrezca el servicio. Con varios servicios se combina uno por cada uno: debe tenerlos todos. */
	public static Specification<Alojamiento> conServicio(Short servicioId) {
		return (raiz, consulta, cb) -> {
			Subquery<Long> subconsulta = consulta.subquery(Long.class);
			Root<Alojamiento> otro = subconsulta.from(Alojamiento.class);
			Join<Alojamiento, Servicio> servicio = otro.join("servicios");
			subconsulta.select(otro.get("id"))
					.where(cb.equal(otro, raiz), cb.equal(servicio.get("id"), servicioId));
			return cb.exists(subconsulta);
		};
	}

	/**
	 * Disponible para el rango completo (MA-61, escenario 2): un periodo disponible del anfitrión
	 * contiene todas las noches y ninguna reserva vigente se cruza con ellas. Una retención de pago
	 * vencida ya no ocupa las fechas, igual que al reservar.
	 */
	public static Specification<Alojamiento> disponibleEntre(LocalDate llegada, LocalDate salida, Instant ahora) {
		return (raiz, consulta, cb) -> {
			Subquery<Long> periodo = consulta.subquery(Long.class);
			Root<PeriodoDisponible> disponible = periodo.from(PeriodoDisponible.class);
			periodo.select(disponible.get("id")).where(
					cb.equal(disponible.get(ALOJAMIENTO), raiz),
					cb.lessThanOrEqualTo(disponible.get("fechaInicio"), llegada),
					cb.greaterThanOrEqualTo(disponible.get("fechaFin"), salida));

			Subquery<Long> cruce = consulta.subquery(Long.class);
			Root<PeriodoReservado> reservado = cruce.from(PeriodoReservado.class);
			cruce.select(reservado.get("id")).where(
					cb.equal(reservado.get(ALOJAMIENTO), raiz),
					cb.isNull(reservado.get("liberadoEn")),
					cb.or(cb.isNull(reservado.get("retenidoHasta")),
							cb.greaterThan(reservado.get("retenidoHasta"), ahora)),
					cb.lessThan(reservado.get("fechaLlegada"), salida),
					cb.greaterThan(reservado.get("fechaSalida"), llegada));

			return cb.and(cb.exists(periodo), cb.not(cb.exists(cruce)));
		};
	}
}
