package co.edu.unbosque.marketplace.usuarios.infraestructura;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

/**
 * Crea el primer administrador al arrancar, porque nadie puede registrarse con ese rol. Equivale
 * al {@code LoadDatabase} de Software 1, pero sin contraseñas escritas en el código: el correo y la
 * contraseña salen de {@code ADMIN_EMAIL} y {@code ADMIN_PASSWORD}. Si alguno está vacío no hace
 * nada, así que un ambiente que no los define no tiene este administrador.
 * <p>
 * Con dos réplicas arrancando a la vez, las dos pueden intentar crearlo: la restricción única del
 * correo deja uno solo y la otra réplica sigue arrancando normalmente.
 */
@Configuration
public class CargaDatosDesarrollo {

	private static final Logger log = LoggerFactory.getLogger(CargaDatosDesarrollo.class);

	@Bean
	CommandLineRunner crearAdministradorInicial(UsuarioRepository usuarios, PasswordEncoder passwordEncoder,
			Clock reloj,
			@Value("${marketplace.admin-inicial.email:}") String email,
			@Value("${marketplace.admin-inicial.password:}") String password) {
		return args -> {
			if (email.isBlank() || password.isBlank()) {
				log.info("ADMIN_EMAIL o ADMIN_PASSWORD vacíos: no se crea el administrador inicial");
				return;
			}
			String correo = Usuario.normalizarEmail(email);
			if (usuarios.existsByEmail(correo)) {
				return;
			}
			try {
				usuarios.saveAndFlush(new Usuario("Administrador", correo, "0000000",
						passwordEncoder.encode(password), Rol.ADMINISTRADOR, reloj.instant()));
				log.info("Administrador inicial creado");
			} catch (DataIntegrityViolationException e) {
				log.info("Otra réplica ya creó el administrador inicial");
			}
		};
	}
}
