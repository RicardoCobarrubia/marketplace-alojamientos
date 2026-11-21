package co.edu.unbosque.marketplace.perfiles.infraestructura;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import co.edu.unbosque.marketplace.perfiles.dominio.CodigoRecuperacion;
import jakarta.persistence.LockModeType;

public interface CodigoRecuperacionRepository extends JpaRepository<CodigoRecuperacion, Long> {

	/** El último código del usuario, el único que vale, bloqueado para contar el intento. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<CodigoRecuperacion> findFirstByUsuarioIdOrderByIdDesc(Long usuarioId);

	/** Cuántos códigos se le crearon desde un momento, para el límite diario. */
	long countByUsuarioIdAndCreadoEnAfter(Long usuarioId, Instant desde);
}
