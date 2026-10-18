package com.bea.servicedesk.service;

import com.bea.servicedesk.model.Incident;
import com.bea.servicedesk.model.Role;
import com.bea.servicedesk.model.UserAccount;
import com.bea.servicedesk.repo.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the current user and enforces role and record-ownership rules. */
@Service
public class AccessService {
  private final UserRepository users;

  /**
   * Creates the service.
   *
   * @param users user repository
   */
  public AccessService(UserRepository users) {
    this.users = users;
  }

  /**
   * Returns the authenticated user for the current request.
   *
   * @return current user account
   * @throws ResponseStatusException with 401 when no matching account exists
   */
  public UserAccount me() {
    String email = SecurityContextHolder.getContext().getAuthentication().getName();
    return users
        .findByEmailIgnoreCase(email)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  }

  /**
   * Rejects the request unless the current user has the given role.
   *
   * @param role required role
   * @throws ResponseStatusException with 403 when the role does not match
   */
  public void require(Role role) {
    if (me().role != role) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient role");
    }
  }

  /**
   * Allows administrators, the requester, or the assigned engineer to view an incident.
   *
   * @param incident incident being accessed
   * @throws ResponseStatusException with 403 for any other user
   */
  public void canView(Incident incident) {
    UserAccount u = me();
    if (u.role == Role.ADMIN) {
      return;
    }
    if (u.role == Role.END_USER && incident.requester.id.equals(u.id)) {
      return;
    }
    if (u.role == Role.ENGINEER && incident.assignee != null && incident.assignee.id.equals(u.id)) {
      return;
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Incident not accessible");
  }
}
