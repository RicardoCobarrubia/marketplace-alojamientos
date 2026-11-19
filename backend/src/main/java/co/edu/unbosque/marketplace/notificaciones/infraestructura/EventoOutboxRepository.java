package co.edu.unbosque.marketplace.notificaciones.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplace.notificaciones.dominio.EventoOutbox;

public interface EventoOutboxRepository extends JpaRepository<EventoOutbox, Long> {

	List<EventoOutbox> findByAgregadoAndAgregadoIdOrderByIdAsc(String agregado, Long agregadoId);
}
