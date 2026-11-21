package co.edu.unbosque.marketplace.perfiles.aplicacion;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria.Resultado;
import co.edu.unbosque.marketplace.perfiles.dominio.CodigoRecuperacion;
import co.edu.unbosque.marketplace.perfiles.infraestructura.CodigoRecuperacionRepository;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.EstadoCuenta;
import co.edu.unbosque.marketplace.usuarios.infraestructura.UsuarioRepository;

/**
 * Crea el código de recuperación de un usuario (MA-50, escenario 1). Corre fuera de la petición
 * HTTP ({@link RecuperacionContrasenaService#solicitar}), así lo que hace aquí no cambia el tiempo
 * de respuesta según el correo exista o no.
 * <p>
 * Límites para que nadie adivine un código pidiendo códigos sin parar ni llene el buzón de la
 * víctima: un código por minuto y {@value #CODIGOS_POR_DIA} por día. Con 5 intentos por código, en
 * un día hay como máximo 25 intentos sobre un millón de valores posibles.
 */
@Service
public class EmisionCodigos {

	static final long SEGUNDOS_ENTRE_CODIGOS = 60;
	static final int CODIGOS_POR_DIA = 5;

	/** El código recién creado y el correo al que se envía. */
	public record CodigoEmitido(String destinatario, String codigo) {

		/** El código no aparece si alguien registra el objeto en un log. */
		@Override
		public String toString() {
			return "CodigoEmitido[destinatario=" + destinatario + "]";
		}
	}

	private final UsuarioRepository usuarios;
	private final CodigoRecuperacionRepository codigos;
	private final PasswordEncoder passwordEncoder;
	private final Auditoria auditoria;
	private final Clock reloj;
	private final SecureRandom aleatorio = new SecureRandom();

	public EmisionCodigos(UsuarioRepository usuarios, CodigoRecuperacionRepository codigos,
			PasswordEncoder passwordEncoder, Auditoria auditoria, Clock reloj) {
		this.usuarios = usuarios;
		this.codigos = codigos;
		this.passwordEncoder = passwordEncoder;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	/**
	 * Un código nuevo, o nada si el correo no existe, la cuenta está restringida o ya alcanzó los
	 * límites. La fila del usuario queda bloqueada: dos solicitudes simultáneas se cuentan en orden.
	 */
	@Transactional
	public Optional<CodigoEmitido> emitir(String email) {
		Optional<Usuario> registrado = usuarios.findByEmail(Usuario.normalizarEmail(email))
				.flatMap(usuario -> usuarios.buscarParaActualizar(usuario.getId()))
				.filter(usuario -> usuario.getEstadoCuenta() != EstadoCuenta.RESTRINGIDA);
		if (registrado.isEmpty()) {
			return Optional.empty();
		}
		Usuario usuario = registrado.get();
		Instant ahora = reloj.instant();
		boolean muyPronto = codigos.findFirstByUsuarioIdOrderByIdDesc(usuario.getId())
				.filter(ultimo -> ultimo.getCreadoEn().plusSeconds(SEGUNDOS_ENTRE_CODIGOS).isAfter(ahora))
				.isPresent();
		if (muyPronto || codigos.countByUsuarioIdAndCreadoEnAfter(usuario.getId(),
				ahora.minus(Duration.ofDays(1))) >= CODIGOS_POR_DIA) {
			auditoria.registrar(EventoAuditoria.accion("SOLICITAR_RECUPERACION", "USUARIO", usuario.getId(),
					Resultado.RECHAZADO, "Límite de códigos").conActor(usuario.getId(), usuario.getRol()));
			return Optional.empty();
		}
		String codigo = "%06d".formatted(aleatorio.nextInt(1_000_000));
		codigos.save(new CodigoRecuperacion(usuario.getId(), passwordEncoder.encode(codigo), ahora));
		auditoria.registrar(EventoAuditoria.accion("SOLICITAR_RECUPERACION", "USUARIO", usuario.getId(),
				Resultado.EXITO, null).conActor(usuario.getId(), usuario.getRol()));
		return Optional.of(new CodigoEmitido(usuario.getEmail(), codigo));
	}
}
