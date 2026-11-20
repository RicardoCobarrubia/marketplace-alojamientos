package co.edu.unbosque.marketplace.usuarios.infraestructura;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplace.usuarios.dominio.Usuario;
import jakarta.persistence.LockModeType;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	/** El correo se guarda en minúsculas: quien llama debe normalizarlo con {@link Usuario#normalizarEmail}. */
	Optional<Usuario> findByEmail(String email);

	boolean existsByEmail(String email);

	/**
	 * Para el inicio de sesión: bloquea la fila hasta terminar la transacción. Así dos intentos
	 * simultáneos no leen el mismo contador de intentos fallidos y el bloqueo no se retrasa.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from Usuario u where u.email = :email")
	Optional<Usuario> buscarParaLogin(@Param("email") String email);

	/** Para restringir la cuenta (MA-81): bloquea la fila hasta terminar la transacción. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from Usuario u where u.id = :id")
	Optional<Usuario> buscarParaActualizar(@Param("id") Long id);

	/**
	 * El estado actual de las cuentas, leído de la base de datos con bloqueo compartido: espera a
	 * que termine una restricción en curso (MA-81) y le impide empezar hasta terminar la
	 * transacción.
	 */
	@Query(value = "SELECT estado_cuenta FROM usuario WHERE id IN (:ids) FOR SHARE", nativeQuery = true)
	List<String> estadosConBloqueoCompartido(@Param("ids") List<Long> ids);
}
