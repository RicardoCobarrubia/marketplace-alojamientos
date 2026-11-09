package co.edu.unbosque.marketplace.usuarios.infraestructura;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

/**
 * Solo en desarrollo: crea un administrador para probar, porque nadie puede registrarse con ese
 * rol. Equivale al {@code LoadDatabase} de Software 1, pero sin contraseñas escritas en el código:
 * el correo y la contraseña salen de {@code ADMIN_EMAIL} y {@code ADMIN_PASSWORD} en el .env.
 */
@Configuration
@Profile("dev")
public class CargaDatosDesarrollo {

	private static final Logger log = LoggerFactory.getLogger(CargaDatosDesarrollo.class);

	@Bean
	CommandLineRunner crearAdministradorDeDesarrollo(UsuarioRepository usuarios, PasswordEncoder passwordEncoder,
			Clock reloj,
			@Value("${marketplace.dev.admin.email:}") String email,
			@Value("${marketplace.dev.admin.password:}") String password) {
		return args -> {
			if (email.isBlank() || password.isBlank()) {
				log.info("ADMIN_EMAIL o ADMIN_PASSWORD vacíos: no se crea el administrador de desarrollo");
				return;
			}
			String correo = Usuario.normalizarEmail(email);
			if (usuarios.existsByEmail(correo)) {
				return;
			}
			usuarios.save(new Usuario("Administrador", correo, "0000000",
					passwordEncoder.encode(password), Rol.ADMINISTRADOR, reloj.instant()));
			log.info("Administrador de desarrollo creado");
		};
	}
}
