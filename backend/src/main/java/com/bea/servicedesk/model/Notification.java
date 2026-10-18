package com.bea.servicedesk.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** In-application notification delivered to a single user. */
@Entity
@Table(name = "notifications")
public class Notification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(optional = false)
  public UserAccount recipient;

  @Column(nullable = false)
  public String message;

  public boolean readFlag = false;
  public Instant createdAt = Instant.now();

  /** Creates an empty notification; required by JPA. */
  public Notification() {}

  /**
   * Creates an unread notification.
   *
   * @param recipient user who receives the notification
   * @param message text shown to the user
   */
  public Notification(UserAccount recipient, String message) {
    this.recipient = recipient;
    this.message = message;
  }
}
