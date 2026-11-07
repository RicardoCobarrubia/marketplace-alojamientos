package co.edu.unbosque.marketplace.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplace.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	/** El correo se guarda en minúsculas: quien llama debe normalizarlo con {@link Usuario#normalizarEmail}. */
	Optional<Usuario> findByEmail(String email);

	boolean existsByEmail(String email);
}
