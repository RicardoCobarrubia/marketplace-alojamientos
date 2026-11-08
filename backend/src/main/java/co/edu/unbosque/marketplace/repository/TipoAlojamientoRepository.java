package co.edu.unbosque.marketplace.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplace.model.TipoAlojamiento;

public interface TipoAlojamientoRepository extends JpaRepository<TipoAlojamiento, Short> {

	List<TipoAlojamiento> findAllByOrderByNombreAsc();

	boolean existsByCodigo(String codigo);
}
