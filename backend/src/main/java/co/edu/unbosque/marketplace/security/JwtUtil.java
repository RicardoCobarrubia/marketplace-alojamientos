package co.edu.unbosque.marketplace.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import co.edu.unbosque.marketplace.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Genera y valida los tokens JWT, igual que el JwtUtil de Software 1.
 * <p>
 * Cambios de Software 2: el secreto llega siempre por variable de entorno (sin valor por
 * defecto), usa la API actual de jjwt y el token lleva la versión de sesión ({@code ver}) para
 * poder revocarlo antes de que venza (ADR-010).
 */
@Component
public class JwtUtil {

	static final String CLAIM_ROL = "rol";
	static final String CLAIM_VERSION = "ver";
	private static final int LONGITUD_MINIMA_SECRETO = 32;

	private final SecretKey llave;
	private final Duration validez;
	private final Clock reloj;

	public JwtUtil(@Value("${marketplace.jwt.secreto}") String secreto,
			@Value("${marketplace.jwt.duracion}") Duration validez,
			Clock reloj) {
		byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < LONGITUD_MINIMA_SECRETO) {
			throw new IllegalStateException("marketplace.jwt.secreto (JWT_SECRET) debe tener al menos "
					+ LONGITUD_MINIMA_SECRETO + " caracteres.");
		}
		this.llave = Keys.hmacShaKeyFor(bytes);
		this.validez = validez;
		this.reloj = reloj;
	}

	// ─────────────────────────────────────────
	// Métodos públicos
	// ─────────────────────────────────────────

	/** Genera el token del usuario: el correo va como subject, igual que en Software 1. */
	public String generarToken(Usuario usuario) {
		Instant ahora = reloj.instant();
		return Jwts.builder()
				.subject(usuario.getUsername())
				.claim(CLAIM_ROL, usuario.getRol().name())
				.claim(CLAIM_VERSION, usuario.getVersionSesion())
				.issuedAt(Date.from(ahora))
				.expiration(Date.from(ahora.plus(validez)))
				.signWith(llave)
				.compact();
	}

	/** Extrae el correo si la firma es válida y no ha vencido. */
	public Optional<String> extraerUsername(String token) {
		return leer(token).map(Claims::getSubject);
	}

	public Optional<Instant> extraerExpiracion(String token) {
		return leer(token).map(claims -> claims.getExpiration().toInstant());
	}

	/**
	 * El token es válido si es del usuario, no ha vencido y su versión de sesión es la actual.
	 */
	public boolean validarToken(String token, Usuario usuario) {
		return leer(token)
				.filter(claims -> usuario.getUsername().equals(claims.getSubject()))
				.map(claims -> claims.get(CLAIM_VERSION, Number.class))
				.filter(version -> version.intValue() == usuario.getVersionSesion())
				.isPresent();
	}

	// ─────────────────────────────────────────
	// Métodos privados
	// ─────────────────────────────────────────

	private Optional<Claims> leer(String token) {
		try {
			return Optional.of(Jwts.parser()
					.verifyWith(llave)
					.clock(() -> Date.from(reloj.instant()))
					.build()
					.parseSignedClaims(token)
					.getPayload());
		} catch (JwtException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
