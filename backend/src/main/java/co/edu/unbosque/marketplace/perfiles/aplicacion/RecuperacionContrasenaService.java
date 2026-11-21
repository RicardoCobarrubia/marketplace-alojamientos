package co.edu.unbosque.marketplace.perfiles.aplicacion;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria.Resultado;
import co.edu.unbosque.marketplace.comun.excepcion.SolicitudInvalidaException;
import co.edu.unbosque.marketplace.notificaciones.dominio.CorreoNoEnviadoException;
import co.edu.unbosque.marketplace.notificaciones.dominio.EnvioCorreo;
import co.edu.unbosque.marketplace.perfiles.dominio.CodigoRecuperacion;
import co.edu.unbosque.marketplace.perfiles.infraestructura.CodigoRecuperacionRepository;
import co.edu.unbosque.marketplace.usuarios.aplicacion.SesionesEnCache;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.EstadoCuenta;
import co.edu.unbosque.marketplace.usuarios.infraestructura.UsuarioRepository;
import jakarta.annotation.PreDestroy;

/**
 * Recuperar la contraseña con un código de un solo uso enviado al correo (MA-50).
 * <p>
 * Nunca revela si un correo está registrado: la solicitud responde lo mismo exista o no, y el
 * correo se envía en otro hilo después de confirmar, así la respuesta tampoco tarda distinto. Una
 * cuenta restringida por un administrador no recibe código.
 */
@Service
public class RecuperacionContrasenaService {

	private static final Logger log = LoggerFactory.getLogger(RecuperacionContrasenaService.class);
	private static final String USUARIO = "USUARIO";
	private static final int MAXIMO_BYTES_CONTRASENA = 72;
	/** Entre dos códigos del mismo usuario pasa al menos un minuto: nadie llena su buzón. */
	static final long SEGUNDOS_ENTRE_CODIGOS = 60;

	private final UsuarioRepository usuarios;
	private final CodigoRecuperacionRepository codigos;
	private final PasswordEncoder passwordEncoder;
	private final EnvioCorreo correo;
	private final SesionesEnCache sesiones;
	private final Auditoria auditoria;
	private final Clock reloj;
	private final SecureRandom aleatorio = new SecureRandom();
	private final ExecutorService envios = Executors.newVirtualThreadPerTaskExecutor();

	public RecuperacionContrasenaService(UsuarioRepository usuarios, CodigoRecuperacionRepository codigos,
			PasswordEncoder passwordEncoder, EnvioCorreo correo, SesionesEnCache sesiones, Auditoria auditoria,
			Clock reloj) {
		this.usuarios = usuarios;
		this.codigos = codigos;
		this.passwordEncoder = passwordEncoder;
		this.correo = correo;
		this.sesiones = sesiones;
		this.auditoria = auditoria;
		this.reloj = reloj;
	}

	@PreDestroy
	void detener() {
		envios.close();
	}

	/** MA-50, escenario 1: un código de 6 dígitos que vale 15 minutos. */
	@Transactional
	public void solicitar(String email) {
		Optional<Usuario> registrado = usuarios.findByEmail(Usuario.normalizarEmail(email))
				.flatMap(usuario -> usuarios.buscarParaActualizar(usuario.getId()));
		if (registrado.isEmpty() || registrado.get().getEstadoCuenta() == EstadoCuenta.RESTRINGIDA) {
			return;
		}
		Usuario usuario = registrado.get();
		Instant ahora = reloj.instant();
		boolean muyPronto = codigos.findFirstByUsuarioIdOrderByIdDesc(usuario.getId())
				.filter(ultimo -> ultimo.getCreadoEn().plusSeconds(SEGUNDOS_ENTRE_CODIGOS).isAfter(ahora))
				.isPresent();
		if (muyPronto) {
			return;
		}
		String codigo = "%06d".formatted(aleatorio.nextInt(1_000_000));
		codigos.save(new CodigoRecuperacion(usuario.getId(), passwordEncoder.encode(codigo), ahora));
		auditoria.registrar(EventoAuditoria.accion("SOLICITAR_RECUPERACION", USUARIO, usuario.getId(),
				Resultado.EXITO, null).conActor(usuario.getId(), usuario.getRol()));
		enviarAlConfirmar(usuario.getEmail(), codigo);
	}

	/**
	 * MA-50, escenarios 2 y 3: con el último código, sin usar ni vencer, cambia la contraseña, la
	 * anterior deja de servir y se cierran las sesiones abiertas. Un código equivocado gasta un
	 * intento aunque la solicitud se rechace.
	 */
	@Transactional(noRollbackFor = SolicitudInvalidaException.class)
	public void cambiarContrasena(NuevaContrasenaDTO solicitud) {
		if (solicitud.password().getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES_CONTRASENA) {
			throw new SolicitudInvalidaException("CONTRASENA_MUY_LARGA",
					"La contraseña es demasiado larga; usa menos caracteres especiales.");
		}
		Usuario usuario = usuarios.findByEmail(Usuario.normalizarEmail(solicitud.email()))
				.flatMap(encontrado -> usuarios.buscarParaActualizar(encontrado.getId()))
				.orElseThrow(RecuperacionContrasenaService::codigoInvalido);
		Instant ahora = reloj.instant();
		CodigoRecuperacion codigo = codigos.findFirstByUsuarioIdOrderByIdDesc(usuario.getId())
				.filter(ultimo -> ultimo.sirve(ahora))
				.orElseThrow(RecuperacionContrasenaService::codigoInvalido);
		if (!passwordEncoder.matches(solicitud.codigo(), codigo.getCodigoHash())) {
			codigo.registrarIntentoFallido();
			auditoria.registrar(EventoAuditoria.accion("CAMBIAR_CONTRASENA", USUARIO, usuario.getId(),
					Resultado.RECHAZADO, "Código incorrecto").conActor(usuario.getId(), usuario.getRol()));
			throw codigoInvalido();
		}
		codigo.usar(ahora);
		usuario.cambiarContrasena(passwordEncoder.encode(solicitud.password()));
		auditoria.registrar(EventoAuditoria.accion("CAMBIAR_CONTRASENA", USUARIO, usuario.getId(),
				Resultado.EXITO, "Con código de recuperación").conActor(usuario.getId(), usuario.getRol()));
		sesiones.olvidarAlConfirmar(usuario.getEmail());
		log.info("Contraseña cambiada con código de recuperación (usuario {})", usuario.getId());
	}

	/** El mismo error para un correo que no existe y para un código usado, vencido o equivocado. */
	private static SolicitudInvalidaException codigoInvalido() {
		return new SolicitudInvalidaException("CODIGO_INVALIDO",
				"El código no es válido o ya venció. Solicita uno nuevo.");
	}

	private void enviarAlConfirmar(String destinatario, String codigo) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				envios.execute(() -> enviar(destinatario, codigo));
			}
		});
	}

	private void enviar(String destinatario, String codigo) {
		try {
			correo.enviar(destinatario, "Código para recuperar tu contraseña",
					"Tu código para recuperar la contraseña en LR Alojamientos es " + codigo + ".\n\n"
							+ "Vence en 15 minutos y sirve una sola vez. Si no lo pediste, ignora este correo:"
							+ " tu contraseña no cambia.");
		} catch (CorreoNoEnviadoException e) {
			// Sin el código en el log; el usuario puede pedir otro en un minuto
			log.warn("No se pudo enviar el código de recuperación", e);
		}
	}
}
