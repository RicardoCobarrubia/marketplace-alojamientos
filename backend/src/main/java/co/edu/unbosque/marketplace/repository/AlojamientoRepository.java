package co.edu.unbosque.marketplace.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplace.model.Alojamiento;
import co.edu.unbosque.marketplace.model.Alojamiento.EstadoAlojamiento;

public interface AlojamientoRepository extends JpaRepository<Alojamiento, Long> {

	List<Alojamiento> findByAnfitrionIdOrderByCreadoEnDesc(Long anfitrionId);

	List<Alojamiento> findByEstadoOrderByActualizadoEnAsc(EstadoAlojamiento estado);

	List<Alojamiento> findAllByOrderByCreadoEnDesc();
}
