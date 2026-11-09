package co.edu.unbosque.marketplace.usuarios.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Usuario de la plataforma: huésped, anfitrión o administrador.
 * <p>
 * Igual que en Software 1, implementa {@link UserDetails} para que Spring Security lo use
 * directamente y el rol es un enum de la propia clase. De Software 2 agrega el estado de la
 * cuenta, el bloqueo por intentos fallidos y la versión de sesión: cada token lleva la versión
 * con la que se emitió y deja de servir cuando esta cambia (ADR-010).
 */
@Entity
@Table(name = "usuario")
public class Usuario implements UserDetails {

	private static final long serialVersionUID = 1L;

	// ─────────────────────────────────────────
	// Identidad — MA-47
	// ─────────────────────────────────────────

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String nombre;

	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@Column(nullable = false, length = 20)
	private String telefono;

	/** Hash BCrypt. La contraseña en texto plano nunca se guarda (MA-47, escenario 3). */
	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	// ─────────────────────────────────────────
	// Rol — MA-49
	// ─────────────────────────────────────────

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Rol rol;

	// ─────────────────────────────────────────
	// Estado de la cuenta y sesión — MA-48, ADR-010
	// ─────────────────────────────────────────

	@Enumerated(EnumType.STRING)
	@Column(name = "estado_cuenta", nullable = false, length = 20)
	private EstadoCuenta estadoCuenta;

	/** Se incrementa para invalidar todos los tokens emitidos (desactivar, restringir...). */
	@Column(name = "version_sesion", nullable = false)
	private int versionSesion;

	@Column(name = "intentos_fallidos", nullable = false)
	private short intentosFallidos;

	@Column(name = "bloqueado_hasta")
	private Instant bloqueadoHasta;

	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	// ─────────────────────────────────────────
	// Enums
	// ─────────────────────────────────────────

	public enum Rol {
		HUESPED,
		ANFITRION,
		ADMINISTRADOR;

		/** Solo huésped y anfitrión se pueden elegir al registrarse (MA-47, escenario 2). */
		public boolean sePuedeElegirAlRegistrarse() {
			return this != ADMINISTRADOR;
		}
	}

	public enum EstadoCuenta {
		/** Puede usar la plataforma. */
		ACTIVA,
		/** El propio usuario la desactivó; solo puede reactivarla (MA-52). */
		DESACTIVADA,
		/** Un administrador la restringió; no puede iniciar sesión (MA-81). */
		RESTRINGIDA
	}

	// ─────────────────────────────────────────
	// Constructores
	// ─────────────────────────────────────────

	protected Usuario() {
		// Requerido por JPA
	}

	/** Crea un usuario nuevo y activo. El correo se normaliza en minúsculas. */
	public Usuario(String nombre, String email, String telefono, String passwordHash, Rol rol, Instant ahora) {
		this.nombre = nombre.trim();
		this.email = normalizarEmail(email);
		this.telefono = telefono.trim();
		this.passwordHash = passwordHash;
		this.rol = rol;
		this.estadoCuenta = EstadoCuenta.ACTIVA;
		this.versionSesion = 0;
		this.intentosFallidos = 0;
		this.creadoEn = ahora;
	}

	public static String normalizarEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	// ─────────────────────────────────────────
	// UserDetails — requerido por Spring Security
	// ─────────────────────────────────────────

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
	}

	/** El correo actúa como username, igual que en Software 1. */
	@Override
	public String getUsername() {
		return getEmail();
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	/** Una cuenta desactivada por su dueño no puede entrar hasta reactivarla. */
	@Override
	public boolean isEnabled() {
		return estadoCuenta != EstadoCuenta.DESACTIVADA;
	}

	/** Una cuenta restringida por un administrador no puede entrar. */
	@Override
	public boolean isAccountNonLocked() {
		return estadoCuenta != EstadoCuenta.RESTRINGIDA;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	// ─────────────────────────────────────────
	// Métodos de negocio — MA-48
	// ─────────────────────────────────────────

	/** Tras varios intentos fallidos el inicio de sesión queda bloqueado un tiempo. */
	public boolean estaBloqueadoTemporalmente(Instant ahora) {
		return bloqueadoHasta != null && ahora.isBefore(bloqueadoHasta);
	}

	/**
	 * Suma un intento fallido. Al llegar a {@code intentosMaximos} bloquea el inicio de sesión
	 * durante {@code duracionBloqueo} y reinicia el contador.
	 */
	public void registrarIntentoFallido(Instant ahora, int intentosMaximos, Duration duracionBloqueo) {
		intentosFallidos++;
		if (intentosFallidos >= intentosMaximos) {
			bloqueadoHasta = ahora.plus(duracionBloqueo);
			intentosFallidos = 0;
		}
	}

	public void registrarInicioSesionExitoso() {
		intentosFallidos = 0;
		bloqueadoHasta = null;
	}

	public boolean estaActiva() {
		return estadoCuenta == EstadoCuenta.ACTIVA;
	}

	// ─────────────────────────────────────────
	// Getters
	// ─────────────────────────────────────────

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public String getEmail() {
		return email;
	}

	public String getTelefono() {
		return telefono;
	}

	public Rol getRol() {
		return rol;
	}

	public EstadoCuenta getEstadoCuenta() {
		return estadoCuenta;
	}

	public int getVersionSesion() {
		return versionSesion;
	}

	public short getIntentosFallidos() {
		return intentosFallidos;
	}

	public Instant getBloqueadoHasta() {
		return bloqueadoHasta;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	/** Evita que el hash de la contraseña aparezca en los logs. */
	@Override
	public String toString() {
		return "Usuario[id=" + id + ", email=" + email + ", rol=" + rol + ", estadoCuenta=" + estadoCuenta + "]";
	}
}
