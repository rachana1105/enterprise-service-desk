package com.bea.servicedesk.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Audit-trail entry recording who changed an incident, what changed and when. */
@Entity
@Table(name = "incident_events")
public class IncidentEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(optional = false)
  public Incident incident;

  @ManyToOne(optional = false)
  public UserAccount actor;

  @Column(nullable = false)
  public String action;

  @Column(length = 3000)
  public String detail;

  public Instant createdAt = Instant.now();

  /** Creates an empty event; required by JPA. */
  public IncidentEvent() {}

  /**
   * Creates an audit event for an incident.
   *
   * @param incident incident that was changed
   * @param actor user who performed the change
   * @param action short action code such as CREATED or ASSIGNED
   * @param detail human-readable detail of the change
   */
  public IncidentEvent(Incident incident, UserAccount actor, String action, String detail) {
    this.incident = incident;
    this.actor = actor;
    this.action = action;
    this.detail = detail;
  }
}
