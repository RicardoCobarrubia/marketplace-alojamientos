package co.edu.unbosque.marketplace.resenas.dominio;

import java.time.Instant;

import co.edu.unbosque.marketplace.comun.excepcion.ReglaNegocioException;
import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Reseña de una estadía (MA-77, MA-78). Guarda el huésped, la reserva, el alojamiento, la fecha y
 * el contenido; el contenido no se puede editar después de publicarla (MA-77, escenario 3). Solo
 * el administrador cambia su visibilidad (MA-79).
 */
@Entity
@Table(name = "resena")
public class Resena {

	public static final int CALIFICACION_MINIMA = 1;
	public static final int CALIFICACION_MAXIMA = 5;
	public static final int LARGO_MINIMO = 10;
	public static final int LARGO_MAXIMO = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "reserva_id", nullable = false, updatable = false, unique = true)
	private Long reservaId;

	@Column(name = "alojamiento_id", nullable = false, updatable = false)
	private Long alojamientoId;

	@Column(name = "huesped_id", nullable = false, updatable = false)
	private Long huespedId;

	@Column(nullable = false, updatable = false)
	private short calificacion;

	@Column(nullable = false, updatable = false, length = LARGO_MAXIMO)
	private String comentario;

	@Column(name = "creada_en", nullable = false, updatable = false)
	private Instant creadaEn;

	@Column(nullable = false)
	private boolean oculta;

	@Column(name = "motivo_ocultamiento", length = 500)
	private String motivoOcultamiento;

	@Column(name = "moderada_en")
	private Instant moderadaEn;

	@Version
	private long version;

	protected Resena() {
		// Requerido por JPA
	}

	/** MA-77, escenario 1: calificación de 1 a 5 y comentario de 10 a 1000 caracteres. */
	public Resena(Long reservaId, Long alojamientoId, Long huespedId, int calificacion, String comentario,
			Instant ahora) {
		if (calificacion < CALIFICACION_MINIMA || calificacion > CALIFICACION_MAXIMA) {
			throw new SolicitudInvalidaException("CALIFICACION_INVALIDA", "La calificación va de 1 a 5.");
		}
		String texto = comentario == null ? "" : comentario.strip();
		if (texto.length() < LARGO_MINIMO || texto.length() > LARGO_MAXIMO) {
			throw new SolicitudInvalidaException("COMENTARIO_INVALIDO",
					"El comentario debe tener entre 10 y 1000 caracteres.");
		}
		this.reservaId = reservaId;
		this.alojamientoId = alojamientoId;
		this.huespedId = huespedId;
		this.calificacion = (short) calificacion;
		this.comentario = texto;
		this.creadaEn = ahora;
	}

	/** MA-79, escenario 1: deja de mostrarse y de contar en el promedio. */
	public void ocultar(String motivo, Instant ahora) {
		if (oculta) {
			throw new ReglaNegocioException("YA_OCULTA", "La reseña ya está oculta.");
		}
		this.oculta = true;
		this.motivoOcultamiento = motivo.strip();
		this.moderadaEn = ahora;
	}

	/** MA-79, escenario 2: se muestra de nuevo. */
	public void mostrar(Instant ahora) {
		if (!oculta) {
			throw new ReglaNegocioException("NO_OCULTA", "La reseña no está oculta.");
		}
		this.oculta = false;
		this.motivoOcultamiento = null;
		this.moderadaEn = ahora;
	}

	/** Para la auditoría: VISIBLE u OCULTA. */
	public String visibilidad() {
		return oculta ? "OCULTA" : "VISIBLE";
	}

	public Long getId() {
		return id;
	}

	public Long getReservaId() {
		return reservaId;
	}

	public Long getAlojamientoId() {
		return alojamientoId;
	}

	public Long getHuespedId() {
		return huespedId;
	}

	public int getCalificacion() {
		return calificacion;
	}

	public String getComentario() {
		return comentario;
	}

	public Instant getCreadaEn() {
		return creadaEn;
	}

	public boolean isOculta() {
		return oculta;
	}

	public String getMotivoOcultamiento() {
		return motivoOcultamiento;
	}

	public Instant getModeradaEn() {
		return moderadaEn;
	}
}
