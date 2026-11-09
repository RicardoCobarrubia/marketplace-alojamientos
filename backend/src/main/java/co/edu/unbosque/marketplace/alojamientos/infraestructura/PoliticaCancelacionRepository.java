package co.edu.unbosque.marketplace.alojamientos.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplace.alojamientos.dominio.PoliticaCancelacion;

public interface PoliticaCancelacionRepository extends JpaRepository<PoliticaCancelacion, Short> {

	List<PoliticaCancelacion> findAllByOrderByNombreAsc();
}
