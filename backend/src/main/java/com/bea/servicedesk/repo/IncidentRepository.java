package com.bea.servicedesk.repo;

import com.bea.servicedesk.model.Incident;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for incidents, including role-scoped list queries. */
public interface IncidentRepository extends JpaRepository<Incident, Long> {
  List<Incident> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

  List<Incident> findByAssigneeIdOrderByCreatedAtDesc(Long assigneeId);

  List<Incident> findAllByOrderByCreatedAtDesc();
}
