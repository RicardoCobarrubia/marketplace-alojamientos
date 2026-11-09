package co.edu.unbosque.marketplace.alojamientos.infraestructura;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplace.alojamientos.dominio.ServicioAdicional;

public interface ServicioAdicionalRepository extends JpaRepository<ServicioAdicional, Long> {
}
