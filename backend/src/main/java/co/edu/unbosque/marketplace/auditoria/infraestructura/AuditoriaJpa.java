package co.edu.unbosque.marketplace.auditoria.infraestructura;

import java.time.Clock;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.auditoria.dominio.RegistroAuditoria;
import co.edu.unbosque.marketplace.comun.auditoria.Auditoria;
import co.edu.unbosque.marketplace.comun.auditoria.EventoAuditoria;
import co.edu.unbosque.marketplace.comun.web.CorrelacionFilter;
import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;

/**
 * Guarda los eventos de auditoría en la base de datos. El actor es el que trae el evento o, si no
 * trae, el usuario autenticado de la petición; en una tarea programada no hay actor. Si ya hay una transacción, el registro va en
 * ella (MA-87, escenario 2).
 */
@Component
public class AuditoriaJpa implements Auditoria {

	private final RegistroAuditoriaRepository repositorio;
	private final Clock reloj;

	public AuditoriaJpa(RegistroAuditoriaRepository repositorio, Clock reloj) {
		this.repositorio = repositorio;
		this.reloj = reloj;
	}

	@Override
	@Transactional
	public void registrar(EventoAuditoria evento) {
		Long actorId = evento.actorId();
		String actorRol = evento.actorRol();
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (actorId == null && autenticacion != null && autenticacion.getPrincipal() instanceof Usuario usuario) {
			actorId = usuario.getId();
			actorRol = usuario.getRol().name();
		}
		repositorio.save(new RegistroAuditoria(evento, actorId, actorRol, MDC.get(CorrelacionFilter.CLAVE_MDC),
				reloj.instant()));
	}
}
