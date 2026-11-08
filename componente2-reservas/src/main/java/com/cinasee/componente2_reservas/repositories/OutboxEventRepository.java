package com.cinasee.componente2_reservas.repositories;

import com.cinasee.componente2_reservas.models.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Acceso a la tabla outbox_events (eventos pendientes de publicar).
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    /** Eventos aún no publicados, del más antiguo al más reciente (respeta el orden). */
    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.createdAt")
    List<OutboxEvent> findPending(Pageable pageable);
}
