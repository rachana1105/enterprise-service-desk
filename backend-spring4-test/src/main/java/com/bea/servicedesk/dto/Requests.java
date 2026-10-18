package com.bea.servicedesk.dto;

import com.bea.servicedesk.model.Priority;
import com.bea.servicedesk.model.Role;
import com.bea.servicedesk.model.Status;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Validated request bodies accepted by the REST API. */
public final class Requests {
  private Requests() {}

  /** Self-registration request for a new employee account. */
  public record Register(
      @NotBlank String name, @Email @NotBlank String email, @Size(min = 8) String password) {}

  /** Login request exchanged for a JWT. */
  public record Login(@Email @NotBlank String email, @NotBlank String password) {}

  /** New incident submitted by an employee or administrator. */
  public record CreateIncident(
      @NotBlank String title,
      @NotBlank String description,
      @NotBlank String category,
      @NotNull Priority priority) {}

  /** Partial update of an incident; every field is optional. */
  public record UpdateIncident(Status status, Long assigneeId, String resolution, String comment) {}

  /** Administrator request to change a user's role. */
  public record AssignRole(@NotNull Role role) {}
}
