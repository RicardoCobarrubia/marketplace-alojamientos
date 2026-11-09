package co.edu.unbosque.marketplace.alojamientos.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplace.alojamientos.dominio.Ciudad;

public interface CiudadRepository extends JpaRepository<Ciudad, Long> {

	List<Ciudad> findAllByOrderByNombreAsc();

	boolean existsByNombreIgnoreCaseAndDepartamentoIgnoreCase(String nombre, String departamento);
}
