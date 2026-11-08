package co.edu.unbosque.marketplace.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplace.model.Ciudad;

public interface CiudadRepository extends JpaRepository<Ciudad, Long> {

	List<Ciudad> findAllByOrderByNombreAsc();

	boolean existsByNombreIgnoreCaseAndDepartamentoIgnoreCase(String nombre, String departamento);
}
