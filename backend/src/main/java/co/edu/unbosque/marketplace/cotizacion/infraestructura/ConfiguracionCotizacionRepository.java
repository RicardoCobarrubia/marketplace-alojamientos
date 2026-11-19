package co.edu.unbosque.marketplace.cotizacion.infraestructura;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplace.cotizacion.dominio.ConfiguracionCotizacion;

public interface ConfiguracionCotizacionRepository extends JpaRepository<ConfiguracionCotizacion, Short> {
}
