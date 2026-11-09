package co.edu.unbosque.marketplace.reservas.infraestructura;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplace.reservas.dominio.ClaveIdempotencia;

public interface ClaveIdempotenciaRepository extends JpaRepository<ClaveIdempotencia, Long> {

	Optional<ClaveIdempotencia> findByUsuarioIdAndClave(Long usuarioId, String clave);

	Optional<ClaveIdempotencia> findByReservaId(Long reservaId);
}
