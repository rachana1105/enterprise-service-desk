package com.bea.servicedesk.service;

import com.bea.servicedesk.dto.Requests;
import com.bea.servicedesk.model.Incident;
import com.bea.servicedesk.model.IncidentEvent;
import com.bea.servicedesk.model.Notification;
import com.bea.servicedesk.model.Role;
import com.bea.servicedesk.model.Status;
import com.bea.servicedesk.model.UserAccount;
import com.bea.servicedesk.repo.EventRepository;
import com.bea.servicedesk.repo.IncidentRepository;
import com.bea.servicedesk.repo.NotificationRepository;
import com.bea.servicedesk.repo.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Incident workflow: creation, role-scoped listing, assignment, status changes and comments. */
@Service
public class IncidentService {
  private final IncidentRepository incidents;
  private final EventRepository events;
  private final NotificationRepository notes;
  private final UserRepository users;
  private final AccessService access;

  /**
   * Creates the service.
   *
   * @param incidents incident repository
   * @param events audit event repository
   * @param notes notification repository
   * @param users user repository
   * @param access access-control helper
   */
  public IncidentService(
      IncidentRepository incidents,
      EventRepository events,
      NotificationRepository notes,
      UserRepository users,
      AccessService access) {
    this.incidents = incidents;
    this.events = events;
    this.notes = notes;
    this.users = users;
    this.access = access;
  }

  /**
   * Submits a new incident on behalf of the current user and records a CREATED event.
   *
   * @param input validated incident details
   * @return saved incident with status OPEN
   */
  @Transactional
  public Incident create(Requests.CreateIncident input) {
    UserAccount actor = access.me();
    if (actor.role != Role.END_USER && actor.role != Role.ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    Incident x = new Incident();
    x.title = input.title();
    x.description = input.description();
    x.category = input.category();
    x.priority = input.priority();
    x.requester = actor;
    x = incidents.save(x);
    events.save(new IncidentEvent(x, actor, "CREATED", "Incident submitted"));
    return x;
  }

  /**
   * Lists incidents visible to the current user: all for admins, assigned ones for engineers,
   * own ones for employees.
   *
   * @return incidents, newest first
   */
  public List<Incident> list() {
    UserAccount u = access.me();
    return switch (u.role) {
      case ADMIN -> incidents.findAllByOrderByCreatedAtDesc();
      case ENGINEER -> incidents.findByAssigneeIdOrderByCreatedAtDesc(u.id);
      case END_USER -> incidents.findByRequesterIdOrderByCreatedAtDesc(u.id);
    };
  }

  /**
   * Loads one incident after checking the current user may view it.
   *
   * @param id incident id
   * @return incident
   */
  public Incident get(Long id) {
    Incident i =
        incidents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    access.canView(i);
    return i;
  }

  /**
   * Returns the audit timeline of an incident the current user may view.
   *
   * @param id incident id
   * @return events, oldest first
   */
  public List<IncidentEvent> history(Long id) {
    get(id);
    return events.findByIncidentIdOrderByCreatedAtAsc(id);
  }

  /**
   * Applies an assignment, status change, resolution or comment to an incident.
   *
   * @param id incident id
   * @param input requested changes
   * @return updated incident
   */
  @Transactional
  public Incident update(Long id, Requests.UpdateIncident input) {
    Incident x = get(id);
    UserAccount u = access.me();
    if (u.role == Role.END_USER) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    if (input.assigneeId() != null) {
      assign(x, u, input.assigneeId());
    }
    if (input.status() != null) {
      changeStatus(x, u, input.status(), input.resolution());
    }
    if (input.resolution() != null && !input.resolution().isBlank()) {
      x.resolution = input.resolution();
      events.save(new IncidentEvent(x, u, "RESOLUTION", input.resolution()));
    }
    if (input.comment() != null && !input.comment().isBlank()) {
      events.save(new IncidentEvent(x, u, "COMMENT", input.comment()));
    }
    x.updatedAt = Instant.now();
    return incidents.save(x);
  }

  private void assign(Incident x, UserAccount actor, Long assigneeId) {
    access.require(Role.ADMIN);
    UserAccount engineer =
        users
            .findById(assigneeId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown engineer"));
    if (engineer.role != Role.ENGINEER) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee must be an engineer");
    }
    x.assignee = engineer;
    x.status = Status.ASSIGNED;
    events.save(new IncidentEvent(x, actor, "ASSIGNED", engineer.name));
    notes.save(new Notification(engineer, "Ticket #" + x.id + " assigned to you"));
  }

  private void changeStatus(Incident x, UserAccount actor, Status requested, String resolution) {
    if (requested == Status.OPEN || requested == Status.ASSIGNED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status transition");
    }
    if (x.assignee == null && actor.role != Role.ADMIN) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assign engineer first");
    }
    boolean finishing = requested == Status.RESOLVED || requested == Status.CLOSED;
    boolean noNewResolution = resolution == null || resolution.isBlank();
    boolean noSavedResolution = x.resolution == null || x.resolution.isBlank();
    if (finishing && noNewResolution && noSavedResolution) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resolution required");
    }
    x.status = requested;
    events.save(new IncidentEvent(x, actor, "STATUS_CHANGED", requested.name()));
    notes.save(new Notification(x.requester, "Ticket #" + x.id + " is " + requested));
  }

  /**
   * Adds a comment to the audit timeline of an incident the current user may view.
   *
   * @param id incident id
   * @param comment comment text
   * @return the incident
   */
  @Transactional
  public Incident addComment(Long id, String comment) {
    Incident i = get(id);
    if (comment == null || comment.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment is required");
    }
    events.save(new IncidentEvent(i, access.me(), "COMMENT", comment));
    return i;
  }
}
