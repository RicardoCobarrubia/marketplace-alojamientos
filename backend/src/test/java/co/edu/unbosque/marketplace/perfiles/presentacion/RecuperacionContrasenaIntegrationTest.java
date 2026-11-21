package co.edu.unbosque.marketplace.perfiles.presentacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import co.edu.unbosque.marketplace.ApiDePrueba;
import co.edu.unbosque.marketplace.TestcontainersConfiguration;
import co.edu.unbosque.marketplace.notificaciones.presentacion.BuzonDePrueba;

/** Recuperar la contraseña contra PostgreSQL real (MA-50). Requiere Docker en ejecución. */
@Import({ TestcontainersConfiguration.class, RecuperacionContrasenaIntegrationTest.Buzon.class })
@SpringBootTest
@ActiveProfiles("test")
class RecuperacionContrasenaIntegrationTest {

	private static final String CORREO = "huesped@correo.com";
	private static final String NUEVA = "clave-nueva-1";
	private static final Pattern CODIGO = Pattern.compile("es (\\d{6})\\.");

	@TestConfiguration
	static class Buzon {
		@Bean
		@Primary
		BuzonDePrueba buzonDePrueba() {
			return new BuzonDePrueba();
		}
	}

	@Autowired
	private WebApplicationContext contexto;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private CacheManager cacheManager;
	@Autowired
	private BuzonDePrueba buzon;

	private ApiDePrueba api;
	private String token;
	/** Último registro de auditoría antes de la prueba: la tabla es inmutable y los ids se reinician. */
	private long auditoriaDesde;

	@BeforeEach
	void preparar() throws Exception {
		api = new ApiDePrueba(contexto, jdbc, passwordEncoder, cacheManager);
		buzon.reiniciar();
		token = api.registrarEIniciarSesion(CORREO, "HUESPED");
		Long ultimo = jdbc.queryForObject("SELECT max(id) FROM auditoria", Long.class);
		auditoriaDesde = ultimo == null ? 0 : ultimo;
	}

	private ResultActions enviar(String ruta, String cuerpo) throws Exception {
		return api.mvc().perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private ResultActions pedirCodigo(String correo) throws Exception {
		return enviar("/api/auth/recuperacion", "{\"email\":\"%s\"}".formatted(correo));
	}

	private ResultActions cambiar(String codigo, String password) throws Exception {
		return enviar("/api/auth/recuperacion/confirmacion",
				"{\"email\":\"%s\",\"codigo\":\"%s\",\"password\":\"%s\"}".formatted(CORREO, codigo, password));
	}

	private ResultActions iniciarSesion(String password) throws Exception {
		return enviar("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(CORREO, password));
	}

	private String codigoRecibido() {
		await().atMost(Duration.ofSeconds(5)).until(() -> !buzon.recibidos().isEmpty());
		BuzonDePrueba.Correo recibido = buzon.recibidos().getLast();
		assertThat(recibido.destinatario()).isEqualTo(CORREO);
		Matcher codigo = CODIGO.matcher(recibido.cuerpo());
		assertThat(codigo.find()).isTrue();
		return codigo.group(1);
	}

	@Test
	void conElCodigoDelCorreoCambiaLaContrasenaYLaAnteriorDejaDeServir() throws Exception {
		pedirCodigo(CORREO).andExpect(status().isAccepted())
				.andExpect(jsonPath("$.mensaje").value(RecuperacionContrasenaController.SOLICITUD_RECIBIDA));
		String codigo = codigoRecibido();
		String otro = codigo.equals("000000") ? "000001" : "000000";

		cambiar(otro, NUEVA).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
		cambiar(codigo, NUEVA).andExpect(status().isNoContent());

		iniciarSesion(ApiDePrueba.CLAVE).andExpect(status().isUnauthorized());
		iniciarSesion(NUEVA).andExpect(status().isOk());
		// Las sesiones abiertas antes del cambio se cierran
		api.como(token, get("/api/usuarios/me")).andExpect(status().isUnauthorized());
		// Un solo uso
		cambiar(codigo, "otra-clave-22").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
		assertThat(jdbc.queryForObject("SELECT intentos FROM codigo_recuperacion", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForList("SELECT resultado FROM auditoria WHERE id > ? AND accion = 'CAMBIAR_CONTRASENA'"
				+ " ORDER BY id", String.class, auditoriaDesde)).containsExactly("RECHAZADO", "EXITO");
	}

	@Test
	void unCodigoVencidoSeRechaza() throws Exception {
		pedirCodigo(CORREO).andExpect(status().isAccepted());
		String codigo = codigoRecibido();
		jdbc.update("UPDATE codigo_recuperacion SET creado_en = now() - interval '20 minutes',"
				+ " vence_en = now() - interval '5 minutes'");

		cambiar(codigo, NUEVA).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
		iniciarSesion(ApiDePrueba.CLAVE).andExpect(status().isOk());
	}

	@Test
	void noRevelaSiElCorreoExisteNiPermiteLlenarElBuzon() throws Exception {
		pedirCodigo("nadie@correo.com").andExpect(status().isAccepted())
				.andExpect(jsonPath("$.mensaje").value(RecuperacionContrasenaController.SOLICITUD_RECIBIDA));
		pedirCodigo(CORREO).andExpect(status().isAccepted());
		pedirCodigo(CORREO).andExpect(status().isAccepted());

		codigoRecibido();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM codigo_recuperacion", Integer.class)).isEqualTo(1);
		await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2))
				.until(() -> buzon.recibidos().size() == 1);

		pedirCodigo("no-es-correo").andExpect(status().isBadRequest());
		cambiar("12ab", NUEVA).andExpect(status().isBadRequest());
		cambiar("123456", "corta").andExpect(status().isBadRequest());
	}
}
