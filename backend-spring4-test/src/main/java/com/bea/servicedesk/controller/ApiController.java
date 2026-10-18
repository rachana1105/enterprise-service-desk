package com.bea.servicedesk.controller;

import com.bea.servicedesk.config.JwtService;
import com.bea.servicedesk.dto.Requests;
import com.bea.servicedesk.model.Incident;
import com.bea.servicedesk.model.IncidentEvent;
import com.bea.servicedesk.model.Notification;
import com.bea.servicedesk.model.Priority;
import com.bea.servicedesk.model.Role;
import com.bea.servicedesk.model.Status;
import com.bea.servicedesk.model.UserAccount;
import com.bea.servicedesk.repo.NotificationRepository;
import com.bea.servicedesk.repo.UserRepository;
import com.bea.servicedesk.service.AccessService;
import com.bea.servicedesk.service.IncidentService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** REST endpoints under /api for authentication, incidents, notifications and reports. */
@RestController
@RequestMapping("/api")
public class ApiController {
  private final UserRepository users;
  private final IncidentService incidents;
  private final AccessService access;
  private final NotificationRepository notes;
  private final JwtService jwt;
  private final PasswordEncoder encoder;

  /**
   * Creates the controller with its Spring-managed collaborators.
   *
   * @param users user repository
   * @param incidents incident workflow service
   * @param access access-control helper
   * @param notes notification repository
   * @param jwt token service
   * @param encoder password encoder
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "IncidentService is a Spring-managed dependency injected by the container.")
  public ApiController(
      UserRepository users,
      IncidentService incidents,
      AccessService access,
      NotificationRepository notes,
      JwtService jwt,
      PasswordEncoder encoder) {
    this.users = users;
    this.incidents = incidents;
    this.access = access;
    this.notes = notes;
    this.jwt = jwt;
    this.encoder = encoder;
  }

  /** Public view of a user; never exposes the password hash. */
  public record UserView(Long id, String name, String email, Role role) {
    /**
     * Maps an entity to its view.
     *
     * @param u user entity
     * @return view
     */
    public static UserView of(UserAccount u) {
      return new UserView(u.id, u.name, u.email, u.role);
    }
  }

  /** JSON view of an incident. */
  public record IncidentView(
      Long id,
      String title,
      String description,
      String category,
      Priority priority,
      Status status,
      UserView requester,
      UserView assignee,
      String resolution,
      Instant createdAt,
      Instant updatedAt) {
    /**
     * Maps an entity to its view.
     *
     * @param i incident entity
     * @return view
     */
    public static IncidentView of(Incident i) {
      return new IncidentView(
          i.id,
          i.title,
          i.description,
          i.category,
          i.priority,
          i.status,
          UserView.of(i.requester),
          i.assignee == null ? null : UserView.of(i.assignee),
          i.resolution,
          i.createdAt,
          i.updatedAt);
    }
  }

  /** JSON view of an audit-trail event. */
  public record EventView(String action, String detail, String actor, Instant createdAt) {
    /**
     * Maps an entity to its view.
     *
     * @param e event entity
     * @return view
     */
    public static EventView of(IncidentEvent e) {
      return new EventView(e.action, e.detail, e.actor.name, e.createdAt);
    }
  }

  /** Request body for posting a comment. */
  public record CommentInput(String comment) {}

  /** JSON view of a notification. */
  public record NotificationView(Long id, String message, boolean readFlag, Instant createdAt) {}

  /**
   * Registers a new employee account and returns a token.
   *
   * @param input validated registration data
   * @return 201 with token and user, or 409 when the email exists
   */
  @PostMapping("/auth/register")
  public ResponseEntity<?> register(@Valid @RequestBody Requests.Register input) {
    if (users.findByEmailIgnoreCase(input.email()).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
    }
    UserAccount u =
        users.save(
            new UserAccount(
                input.name(),
                input.email().toLowerCase(Locale.ROOT),
                encoder.encode(input.password()),
                Role.END_USER));
    return ResponseEntity.status(201)
        .body(Map.of("token", jwt.issue(u.email), "user", UserView.of(u)));
  }

  /**
   * Authenticates a user and returns a token.
   *
   * @param input email and password
   * @return token and user, or 401 for wrong credentials
   */
  @PostMapping("/auth/login")
  public Map<String, Object> login(@Valid @RequestBody Requests.Login input) {
    UserAccount u =
        users
            .findByEmailIgnoreCase(input.email())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    if (!encoder.matches(input.password(), u.passwordHash)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
    return Map.of("token", jwt.issue(u.email), "user", UserView.of(u));
  }

  /**
   * Returns the current user.
   *
   * @return current user view
   */
  @GetMapping("/me")
  public UserView me() {
    return UserView.of(access.me());
  }

  /**
   * Lists all users (administrators only).
   *
   * @return user views
   */
  @GetMapping("/users")
  public List<UserView> users() {
    access.require(Role.ADMIN);
    return this.users.findAll().stream().map(UserView::of).toList();
  }

  /**
   * Lists support engineers for assignment (administrators only).
   *
   * @return engineer views
   */
  @GetMapping("/engineers")
  public List<UserView> engineers() {
    access.require(Role.ADMIN);
    return users.findAll().stream().filter(u -> u.role == Role.ENGINEER).map(UserView::of).toList();
  }

  /**
   * Changes another user's role (administrators only).
   *
   * @param id target user id
   * @param input new role
   * @return updated user view
   */
  @PatchMapping("/users/{id}/role")
  public UserView changeRole(@PathVariable Long id, @Valid @RequestBody Requests.AssignRole input) {
    access.require(Role.ADMIN);
    UserAccount u =
        users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (access.me().id.equals(id)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot change own role");
    }
    u.role = input.role();
    return UserView.of(users.save(u));
  }

  /**
   * Lists incidents visible to the current user.
   *
   * @return incident views
   */
  @GetMapping("/incidents")
  public List<IncidentView> all() {
    return incidents.list().stream().map(IncidentView::of).toList();
  }

  /**
   * Creates an incident.
   *
   * @param input validated incident details
   * @return 201 with the new incident
   */
  @PostMapping("/incidents")
  public ResponseEntity<IncidentView> create(@Valid @RequestBody Requests.CreateIncident input) {
    return ResponseEntity.status(201).body(IncidentView.of(incidents.create(input)));
  }

  /**
   * Returns one incident.
   *
   * @param id incident id
   * @return incident view
   */
  @GetMapping("/incidents/{id}")
  public IncidentView one(@PathVariable Long id) {
    return IncidentView.of(incidents.get(id));
  }

  /**
   * Updates an incident (engineer or administrator).
   *
   * @param id incident id
   * @param input requested changes
   * @return updated incident view
   */
  @PatchMapping("/incidents/{id}")
  public IncidentView update(@PathVariable Long id, @RequestBody Requests.UpdateIncident input) {
    return IncidentView.of(incidents.update(id, input));
  }

  /**
   * Returns the audit timeline of an incident.
   *
   * @param id incident id
   * @return event views, oldest first
   */
  @GetMapping("/incidents/{id}/history")
  public List<EventView> history(@PathVariable Long id) {
    return incidents.history(id).stream().map(EventView::of).toList();
  }

  /**
   * Adds a comment to an incident.
   *
   * @param id incident id
   * @param input comment text
   * @return incident view
   */
  @PostMapping("/incidents/{id}/comments")
  public IncidentView comment(@PathVariable Long id, @RequestBody CommentInput input) {
    return IncidentView.of(incidents.addComment(id, input.comment()));
  }

  /**
   * Lists the current user's notifications.
   *
   * @return notification views, newest first
   */
  @GetMapping("/notifications")
  public List<NotificationView> notifications() {
    return notes.findByRecipientIdOrderByCreatedAtDesc(access.me().id).stream()
        .map(n -> new NotificationView(n.id, n.message, n.readFlag, n.createdAt))
        .toList();
  }

  /**
   * Marks one of the current user's notifications as read.
   *
   * @param id notification id
   */
  @PatchMapping("/notifications/{id}/read")
  public void markRead(@PathVariable Long id) {
    Notification n =
        notes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!n.recipient.id.equals(access.me().id)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    n.readFlag = true;
    notes.save(n);
  }

  /**
   * Returns incident counts by status and priority (administrators only).
   *
   * @return totals and breakdowns
   */
  @GetMapping("/reports/summary")
  public Map<String, Object> summary() {
    access.require(Role.ADMIN);
    List<Incident> xs = incidents.list();
    Map<String, Long> statuses = new LinkedHashMap<>();
    for (Status s : Status.values()) {
      statuses.put(s.name(), xs.stream().filter(i -> i.status == s).count());
    }
    Map<String, Long> priorities = new LinkedHashMap<>();
    for (Priority p : Priority.values()) {
      priorities.put(p.name(), xs.stream().filter(i -> i.priority == p).count());
    }
    return Map.of("total", xs.size(), "byStatus", statuses, "byPriority", priorities);
  }
}
