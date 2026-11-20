package co.edu.unbosque.marketplace.notificaciones.dominio;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Evento guardado en la misma transacción que el cambio que lo origina (Transactional Outbox,
 * ADR-007). Si la transacción se revierte, el evento tampoco queda. Lleva el id de correlación
 * de la petición (ADR-012).
 */
@Entity
@Table(name = "evento_outbox")
public class EventoOutbox {

	public enum EstadoEvento {
		PENDIENTE,
		ENTREGADO,
		/** Falló demasiadas veces: se deja de reintentar para no frenar a los demás. */
		FALLIDO
	}

	private static final int MAXIMO_ERROR = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String tipo;

	@Column(nullable = false, length = 30)
	private String agregado;

	@Column(name = "agregado_id", nullable = false)
	private Long agregadoId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String payload;

	@Column(name = "correlation_id", length = 100)
	private String correlationId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoEvento estado;

	@Column(nullable = false)
	private int intentos;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	@Column(name = "entregado_en")
	private Instant entregadoEn;

	@Column(name = "ultimo_error", length = MAXIMO_ERROR)
	private String ultimoError;

	protected EventoOutbox() {
		// Requerido por JPA
	}

	public EventoOutbox(String tipo, String agregado, Long agregadoId, String payload, String correlationId,
			Instant ahora) {
		this.tipo = tipo;
		this.agregado = agregado;
		this.agregadoId = agregadoId;
		this.payload = payload;
		this.correlationId = correlationId;
		this.estado = EstadoEvento.PENDIENTE;
		this.creadoEn = ahora;
	}

	/** Ya se generaron sus notificaciones. */
	public void entregar(Instant ahora) {
		this.estado = EstadoEvento.ENTREGADO;
		this.entregadoEn = ahora;
	}

	/** No se pudo entregar: suma el intento y, al llegar al máximo, deja de reintentarse. */
	public void registrarFallo(String error, int intentosMaximos) {
		this.intentos++;
		this.ultimoError = error == null || error.length() <= MAXIMO_ERROR ? error : error.substring(0, MAXIMO_ERROR);
		if (intentos >= intentosMaximos) {
			this.estado = EstadoEvento.FALLIDO;
		}
	}

	public String getUltimoError() {
		return ultimoError;
	}

	public Long getId() {
		return id;
	}

	public String getTipo() {
		return tipo;
	}

	public String getAgregado() {
		return agregado;
	}

	public Long getAgregadoId() {
		return agregadoId;
	}

	public String getPayload() {
		return payload;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public EstadoEvento getEstado() {
		return estado;
	}

	public int getIntentos() {
		return intentos;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	public Instant getEntregadoEn() {
		return entregadoEn;
	}
}
