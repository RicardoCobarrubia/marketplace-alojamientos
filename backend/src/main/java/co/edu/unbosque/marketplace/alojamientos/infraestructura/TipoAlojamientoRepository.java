package co.edu.unbosque.marketplace.alojamientos.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplace.alojamientos.dominio.TipoAlojamiento;

public interface TipoAlojamientoRepository extends JpaRepository<TipoAlojamiento, Short> {

	List<TipoAlojamiento> findAllByOrderByNombreAsc();

	boolean existsByCodigo(String codigo);
}
