package com.bea.servicedesk.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Service-desk ticket raised by an employee and handled by a support engineer. */
@Entity
@Table(name = "incidents")
public class Incident {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public String title;

  @Column(nullable = false, length = 5000)
  public String description;

  @Column(nullable = false)
  public String category;

  @Enumerated(EnumType.STRING)
  public Priority priority = Priority.MEDIUM;

  @Enumerated(EnumType.STRING)
  public Status status = Status.OPEN;

  @ManyToOne(optional = false)
  public UserAccount requester;

  @ManyToOne public UserAccount assignee;

  @Column(length = 5000)
  public String resolution;

  public Instant createdAt = Instant.now();
  public Instant updatedAt = Instant.now();
}
