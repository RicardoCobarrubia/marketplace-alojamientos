package co.edu.unbosque.marketplace.cotizacion.infraestructura;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplace.cotizacion.dominio.ConfiguracionCotizacion;
import jakarta.persistence.LockModeType;

public interface ConfiguracionCotizacionRepository extends JpaRepository<ConfiguracionCotizacion, Short> {

	/**
	 * Bloquea la fila hasta terminar la transacción: dos administradores que cambian la
	 * configuración a la vez se turnan, y la auditoría guarda el valor que de verdad había antes.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from ConfiguracionCotizacion c where c.id = :id")
	Optional<ConfiguracionCotizacion> buscarParaActualizar(@Param("id") Short id);
}
