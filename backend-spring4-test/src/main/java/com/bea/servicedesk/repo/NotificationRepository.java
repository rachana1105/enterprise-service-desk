package com.bea.servicedesk.repo;

import com.bea.servicedesk.model.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for in-application notifications. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {
  List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long id);
}
