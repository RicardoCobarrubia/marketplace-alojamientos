package co.edu.unbosque.marketplace.auditoria.dominio;

import java.time.Instant;

import org.hibernate.annotations.Immutable;

import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una fila del registro de auditoría. No tiene métodos que la cambien: solo se inserta (MA-87). */
@Entity
@Immutable
@Table(name = "auditoria")
public class RegistroAuditoria {

	private static final int MAXIMO_DETALLE = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "ocurrido_en", nullable = false, updatable = false)
	private Instant ocurridoEn;

	@Column(name = "actor_id", updatable = false)
	private Long actorId;

	@Column(name = "actor_rol", length = 20, updatable = false)
	private String actorRol;

	@Column(nullable = false, length = 60, updatable = false)
	private String accion;

	@Column(length = 40, updatable = false)
	private String entidad;

	@Column(name = "entidad_id", updatable = false)
	private Long entidadId;

	@Column(name = "estado_anterior", length = 30, updatable = false)
	private String estadoAnterior;

	@Column(name = "estado_nuevo", length = 30, updatable = false)
	private String estadoNuevo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private EventoAuditoria.Resultado resultado;

	@Column(length = MAXIMO_DETALLE, updatable = false)
	private String detalle;

	@Column(name = "correlation_id", length = 100, updatable = false)
	private String correlationId;

	protected RegistroAuditoria() {
		// Requerido por JPA
	}

	public RegistroAuditoria(EventoAuditoria evento, Long actorId, String actorRol, String correlationId,
			Instant ahora) {
		this.ocurridoEn = ahora;
		this.actorId = actorId;
		this.actorRol = actorRol;
		this.accion = evento.accion();
		this.entidad = evento.entidad();
		this.entidadId = evento.entidadId();
		this.estadoAnterior = evento.estadoAnterior();
		this.estadoNuevo = evento.estadoNuevo();
		this.resultado = evento.resultado();
		String texto = evento.detalle();
		this.detalle = texto == null || texto.length() <= MAXIMO_DETALLE ? texto : texto.substring(0, MAXIMO_DETALLE);
		this.correlationId = correlationId;
	}

	public Long getId() {
		return id;
	}

	public Instant getOcurridoEn() {
		return ocurridoEn;
	}

	public Long getActorId() {
		return actorId;
	}

	public String getActorRol() {
		return actorRol;
	}

	public String getAccion() {
		return accion;
	}

	public String getEntidad() {
		return entidad;
	}

	public Long getEntidadId() {
		return entidadId;
	}

	public String getEstadoAnterior() {
		return estadoAnterior;
	}

	public String getEstadoNuevo() {
		return estadoNuevo;
	}

	public EventoAuditoria.Resultado getResultado() {
		return resultado;
	}

	public String getDetalle() {
		return detalle;
	}

	public String getCorrelationId() {
		return correlationId;
	}
}
