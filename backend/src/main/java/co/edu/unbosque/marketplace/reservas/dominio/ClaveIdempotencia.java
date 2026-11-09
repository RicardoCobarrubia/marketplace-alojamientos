package co.edu.unbosque.marketplace.reservas.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Registro del Idempotent Receiver (ADR-005, MA-69). La restricción única (usuario, clave) hace
 * que, de varios reintentos simultáneos, solo uno cree la reserva. Los demás reciben la
 * respuesta guardada (TERMINADA) o "en proceso" (EN_CURSO).
 * <p>
 * La huella resume los datos de la solicitud: la misma clave con otros datos es un conflicto.
 */
@Entity
@Table(name = "clave_idempotencia")
public class ClaveIdempotencia {

	public enum EstadoClave {
		EN_CURSO,
		TERMINADA
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Column(nullable = false, length = 100, updatable = false)
	private String clave;

	@Column(nullable = false, length = 64, updatable = false)
	private String huella;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoClave estado;

	@Column(name = "reserva_id")
	private Long reservaId;

	@Column(name = "codigo_http")
	private Integer codigoHttp;

	@Column(columnDefinition = "text")
	private String respuesta;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	protected ClaveIdempotencia() {
		// Requerido por JPA
	}

	public ClaveIdempotencia(Long usuarioId, String clave, String huella, Instant ahora) {
		this.usuarioId = usuarioId;
		this.clave = clave;
		this.huella = huella;
		this.estado = EstadoClave.EN_CURSO;
		this.creadoEn = ahora;
		this.actualizadoEn = ahora;
	}

	public void asociar(Long reservaId) {
		this.reservaId = reservaId;
	}

	/** Guarda la respuesta que recibirán los reintentos. */
	public void terminar(int codigoHttp, String respuesta, Instant ahora) {
		this.estado = EstadoClave.TERMINADA;
		this.codigoHttp = codigoHttp;
		this.respuesta = respuesta;
		this.actualizadoEn = ahora;
	}

	public boolean mismaSolicitud(String otraHuella) {
		return huella.equals(otraHuella);
	}

	public Long getId() {
		return id;
	}

	public String getClave() {
		return clave;
	}

	public EstadoClave getEstado() {
		return estado;
	}

	public Long getReservaId() {
		return reservaId;
	}

	public Integer getCodigoHttp() {
		return codigoHttp;
	}

	public String getRespuesta() {
		return respuesta;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}
}
