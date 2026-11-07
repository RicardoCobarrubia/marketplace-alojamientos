package co.edu.unbosque.marketplace.security;

import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplace.configuration.CacheConfig;
import co.edu.unbosque.marketplace.model.Usuario;
import co.edu.unbosque.marketplace.repository.UsuarioRepository;

/**
 * Carga los usuarios por correo para Spring Security, igual que en Software 1.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

	private final UsuarioRepository usuarioRepository;

	public UserDetailsServiceImpl(UsuarioRepository usuarioRepository) {
		this.usuarioRepository = usuarioRepository;
	}

	/** Lo usa el login: siempre consulta la base de datos. */
	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		return usuarioRepository.findByEmail(Usuario.normalizarEmail(email))
				.orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
	}

	/**
	 * Lo usa {@link JwtAuthenticationFilter} en cada petición. A diferencia de Software 1, el
	 * resultado queda en caché Caffeine como máximo 60 s para no consultar la base de datos en
	 * cada petición (ADR-010): si la cuenta se desactiva o se revoca la sesión, en menos de un
	 * minuto todas las réplicas dejan de aceptar sus tokens.
	 */
	@Cacheable(cacheNames = CacheConfig.VERSIONES_SESION)
	@Transactional(readOnly = true)
	public Optional<Usuario> cargarParaPeticion(String email) {
		return usuarioRepository.findByEmail(Usuario.normalizarEmail(email));
	}
}
