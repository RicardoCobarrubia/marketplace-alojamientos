package co.edu.unbosque.marketplace.alojamientos.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplace.alojamientos.dominio.Servicio;

public interface ServicioRepository extends JpaRepository<Servicio, Short> {

	List<Servicio> findAllByOrderByNombreAsc();

	boolean existsByCodigo(String codigo);
}
