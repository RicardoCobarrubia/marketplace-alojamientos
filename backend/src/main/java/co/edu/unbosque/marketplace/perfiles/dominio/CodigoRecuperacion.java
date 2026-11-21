package co.edu.unbosque.marketplace.perfiles.dominio;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Código de un solo uso para recuperar la contraseña (MA-50). Vale 15 minutos, admite pocos
 * intentos y solo se guarda su hash. Del mismo usuario solo vale el último.
 */
@Entity
@Table(name = "codigo_recuperacion")
public class CodigoRecuperacion {

	/** MA-50, escenario 1. */
	public static final Duration VIGENCIA = Duration.ofMinutes(15);
	/** Con 6 dígitos, 5 intentos por código hacen inútil adivinarlo. */
	public static final int INTENTOS_MAXIMOS = 5;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Column(name = "codigo_hash", nullable = false, updatable = false, length = 100)
	private String codigoHash;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	@Column(name = "vence_en", nullable = false, updatable = false)
	private Instant venceEn;

	@Column(name = "usado_en")
	private Instant usadoEn;

	@Column(nullable = false)
	private short intentos;

	protected CodigoRecuperacion() {
		// Requerido por JPA
	}

	public CodigoRecuperacion(Long usuarioId, String codigoHash, Instant ahora) {
		this.usuarioId = usuarioId;
		this.codigoHash = codigoHash;
		this.creadoEn = ahora;
		this.venceEn = ahora.plus(VIGENCIA);
	}

	/** Sin usar, sin vencer y sin agotar los intentos (MA-50, escenario 3). */
	public boolean sirve(Instant ahora) {
		return usadoEn == null && ahora.isBefore(venceEn) && intentos < INTENTOS_MAXIMOS;
	}

	public void registrarIntentoFallido() {
		intentos++;
	}

	/** Un solo uso: después de cambiar la contraseña ya no sirve. */
	public void usar(Instant ahora) {
		this.usadoEn = ahora;
	}

	public Long getId() {
		return id;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public String getCodigoHash() {
		return codigoHash;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	public Instant getVenceEn() {
		return venceEn;
	}

	public Instant getUsadoEn() {
		return usadoEn;
	}

	public short getIntentos() {
		return intentos;
	}
}
