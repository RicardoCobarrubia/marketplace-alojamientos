package co.edu.unbosque.marketplace.notificaciones.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Notificación interna para un usuario, generada a partir de un evento del outbox (MA-75). */
@Entity
@Table(name = "notificacion")
public class Notificacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Column(name = "evento_id", nullable = false, updatable = false)
	private Long eventoId;

	@Column(nullable = false, length = 50, updatable = false)
	private String tipo;

	@Column(nullable = false, length = 120, updatable = false)
	private String titulo;

	@Column(nullable = false, length = 1000, updatable = false)
	private String mensaje;

	@Column(name = "creada_en", nullable = false, updatable = false)
	private Instant creadaEn;

	@Column(name = "leida_en")
	private Instant leidaEn;

	protected Notificacion() {
		// Requerido por JPA
	}

	public Notificacion(Long usuarioId, Long eventoId, String tipo, String titulo, String mensaje, Instant ahora) {
		this.usuarioId = usuarioId;
		this.eventoId = eventoId;
		this.tipo = tipo;
		this.titulo = titulo;
		this.mensaje = mensaje;
		this.creadaEn = ahora;
	}

	/** La primera lectura queda registrada; las siguientes no la cambian. */
	public void marcarLeida(Instant ahora) {
		if (leidaEn == null) {
			leidaEn = ahora;
		}
	}

	public Long getId() {
		return id;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public Long getEventoId() {
		return eventoId;
	}

	public String getTipo() {
		return tipo;
	}

	public String getTitulo() {
		return titulo;
	}

	public String getMensaje() {
		return mensaje;
	}

	public Instant getCreadaEn() {
		return creadaEn;
	}

	public Instant getLeidaEn() {
		return leidaEn;
	}
}
