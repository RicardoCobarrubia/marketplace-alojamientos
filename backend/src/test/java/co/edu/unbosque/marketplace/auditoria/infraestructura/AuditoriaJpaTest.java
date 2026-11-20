package co.edu.unbosque.marketplace.auditoria.infraestructura;

import static co.edu.unbosque.marketplace.TestDatos.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import co.edu.unbosque.marketplace.TestDatos;
import co.edu.unbosque.marketplace.auditoria.dominio.RegistroAuditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.comun.web.CorrelacionFilter;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario.Rol;

class AuditoriaJpaTest {

	private final RegistroAuditoriaRepository repositorio = mock(RegistroAuditoriaRepository.class);
	private final AuditoriaJpa auditoria = new AuditoriaJpa(repositorio, Clock.fixed(AHORA, ZoneOffset.UTC));

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		MDC.clear();
	}

	private RegistroAuditoria guardado() {
		ArgumentCaptor<RegistroAuditoria> registro = ArgumentCaptor.forClass(RegistroAuditoria.class);
		verify(repositorio).save(registro.capture());
		return registro.getValue();
	}

	@Test
	void registraElActorAutenticadoElCambioYElIdDeCorrelacion() {
		Usuario administrador = TestDatos.usuario(3L, Rol.ADMINISTRADOR, "hash");
		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken
				.authenticated(administrador, null, administrador.getAuthorities()));
		MDC.put(CorrelacionFilter.CLAVE_MDC, "peticion-1");

		auditoria.registrar(EventoAuditoria.cambio("BLOQUEAR_ALOJAMIENTO", "ALOJAMIENTO", 10L, "ACTIVA", "BLOQUEADA",
				"Fotos engañosas"));

		RegistroAuditoria registro = guardado();
		assertThat(registro.getActorId()).isEqualTo(3L);
		assertThat(registro.getActorRol()).isEqualTo("ADMINISTRADOR");
		assertThat(registro.getAccion()).isEqualTo("BLOQUEAR_ALOJAMIENTO");
		assertThat(registro.getEntidad()).isEqualTo("ALOJAMIENTO");
		assertThat(registro.getEntidadId()).isEqualTo(10L);
		assertThat(registro.getEstadoAnterior()).isEqualTo("ACTIVA");
		assertThat(registro.getEstadoNuevo()).isEqualTo("BLOQUEADA");
		assertThat(registro.getResultado()).isEqualTo(EventoAuditoria.Resultado.EXITO);
		assertThat(registro.getDetalle()).isEqualTo("Fotos engañosas");
		assertThat(registro.getCorrelationId()).isEqualTo("peticion-1");
		assertThat(registro.getOcurridoEn()).isEqualTo(AHORA);
		assertThat(registro.getId()).isNull();
	}

	@Test
	void sinUsuarioAutenticadoNoHayActorYElDetalleLargoSeRecorta() {
		auditoria.registrar(EventoAuditoria.accesoDenegado("x".repeat(600)));

		RegistroAuditoria registro = guardado();
		assertThat(registro.getActorId()).isNull();
		assertThat(registro.getActorRol()).isNull();
		assertThat(registro.getResultado()).isEqualTo(EventoAuditoria.Resultado.RECHAZADO);
		assertThat(registro.getAccion()).isEqualTo("ACCESO_DENEGADO");
		assertThat(registro.getDetalle()).hasSize(500);
	}
}
