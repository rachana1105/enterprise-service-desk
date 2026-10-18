package com.bea.servicedesk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bea.servicedesk.model.Incident;
import com.bea.servicedesk.model.Status;
import com.bea.servicedesk.service.IncidentMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for the dashboard metric calculations. */
class IncidentMetricsTest {
  @Test
  void awaitingActionCountsOnlyOpenAndAssigned() {
    Incident open = new Incident();
    Incident assigned = new Incident();
    assigned.status = Status.ASSIGNED;
    Incident resolved = new Incident();
    resolved.status = Status.RESOLVED;
    assertEquals(2, IncidentMetrics.awaitingAction(List.of(open, assigned, resolved)));
  }

  @Test
  void completedCountsResolvedAndClosed() {
    Incident open = new Incident();
    Incident resolved = new Incident();
    resolved.status = Status.RESOLVED;
    Incident closed = new Incident();
    closed.status = Status.CLOSED;
    assertEquals(2, IncidentMetrics.completed(List.of(open, resolved, closed)));
  }
}
