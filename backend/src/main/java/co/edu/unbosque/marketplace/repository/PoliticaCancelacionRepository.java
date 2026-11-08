package co.edu.unbosque.marketplace.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplace.model.PoliticaCancelacion;

public interface PoliticaCancelacionRepository extends JpaRepository<PoliticaCancelacion, Short> {

	List<PoliticaCancelacion> findAllByOrderByNombreAsc();
}
