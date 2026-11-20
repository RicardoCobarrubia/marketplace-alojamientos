package co.edu.unbosque.marketplace.notificaciones.dominio;

import java.time.Instant;

import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Notificación interna para un usuario, generada a partir de un evento del outbox (MA-75).
 * <p>
 * La lectura y el envío del correo cambian columnas distintas y pueden ocurrir a la vez: con
 * {@code @DynamicUpdate} cada uno guarda solo lo que cambió y no pisa lo del otro.
 */
@Entity
@DynamicUpdate
@Table(name = "notificacion")
public class Notificacion {

	/** Estado de la copia por correo. */
	public enum EstadoCorreo {
		PENDIENTE,
		ENVIADO,
		/** Falló demasiadas veces: se deja de reintentar. */
		FALLIDO,
		/** El envío de correos está desactivado. */
		OMITIDO
	}

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

	@Enumerated(EnumType.STRING)
	@Column(name = "correo_estado", nullable = false, length = 20)
	private EstadoCorreo correoEstado;

	@Column(name = "correo_intentos", nullable = false)
	private int correoIntentos;

	@Column(name = "correo_enviado_en")
	private Instant correoEnviadoEn;

	protected Notificacion() {
		// Requerido por JPA
	}

	/**
	 * @param conCorreo si además se envía por correo; queda pendiente hasta que se envíe
	 */
	public Notificacion(Long usuarioId, Long eventoId, String tipo, String titulo, String mensaje, boolean conCorreo,
			Instant ahora) {
		this.correoEstado = conCorreo ? EstadoCorreo.PENDIENTE : EstadoCorreo.OMITIDO;
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

	/** El servidor de correo aceptó la copia. */
	public void correoEnviado(Instant ahora) {
		this.correoEstado = EstadoCorreo.ENVIADO;
		this.correoEnviadoEn = ahora;
	}

	/** No se pudo enviar: suma el intento y, al llegar al máximo, deja de reintentarse. */
	public void correoFallido(int intentosMaximos) {
		this.correoIntentos++;
		if (correoIntentos >= intentosMaximos) {
			this.correoEstado = EstadoCorreo.FALLIDO;
		}
	}

	public EstadoCorreo getCorreoEstado() {
		return correoEstado;
	}

	public int getCorreoIntentos() {
		return correoIntentos;
	}

	public Instant getCorreoEnviadoEn() {
		return correoEnviadoEn;
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
