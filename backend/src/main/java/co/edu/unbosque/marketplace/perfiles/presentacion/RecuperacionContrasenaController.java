package co.edu.unbosque.marketplace.perfiles.presentacion;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplace.perfiles.aplicacion.NuevaContrasenaDTO;
import co.edu.unbosque.marketplace.perfiles.aplicacion.RecuperacionContrasenaService;
import co.edu.unbosque.marketplace.perfiles.aplicacion.SolicitudRecuperacionDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Recuperar la contraseña (MA-50). No piden token: quien la olvidó no puede iniciar sesión. */
@RestController
@RequestMapping("/api/auth/recuperacion")
@Tag(name = "Autenticación")
@SecurityRequirements
public class RecuperacionContrasenaController {

	static final String SOLICITUD_RECIBIDA = "Si el correo está registrado, te enviamos un código que vence en"
			+ " 15 minutos.";

	private final RecuperacionContrasenaService recuperacion;

	public RecuperacionContrasenaController(RecuperacionContrasenaService recuperacion) {
		this.recuperacion = recuperacion;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(summary = "Pedir un código para recuperar la contraseña; responde lo mismo exista o no el correo")
	public Map<String, String> solicitar(@Validated @RequestBody SolicitudRecuperacionDTO solicitud) {
		recuperacion.solicitar(solicitud.email());
		return Map.of("mensaje", SOLICITUD_RECIBIDA);
	}

	@PostMapping("/confirmacion")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Definir una nueva contraseña con el código recibido")
	public void cambiar(@Validated @RequestBody NuevaContrasenaDTO solicitud) {
		recuperacion.cambiarContrasena(solicitud);
	}
}
