package com.bea.servicedesk.repo;

import com.bea.servicedesk.model.IncidentEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for incident audit events. */
public interface EventRepository extends JpaRepository<IncidentEvent, Long> {
  List<IncidentEvent> findByIncidentIdOrderByCreatedAtAsc(Long id);
}
