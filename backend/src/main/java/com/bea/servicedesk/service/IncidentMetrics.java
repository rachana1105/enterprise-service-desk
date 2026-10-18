package com.bea.servicedesk.service;

import com.bea.servicedesk.model.Incident;
import com.bea.servicedesk.model.Status;
import java.util.List;

/** Pure business calculation for dashboard metrics; does not access the database. */
public final class IncidentMetrics {
  private IncidentMetrics() {}

  /**
   * Counts incidents still waiting for work to start.
   *
   * @param incidents incidents to inspect
   * @return number of OPEN or ASSIGNED incidents
   */
  public static long awaitingAction(List<Incident> incidents) {
    return incidents.stream()
        .filter(i -> i.status == Status.OPEN || i.status == Status.ASSIGNED)
        .count();
  }

  /**
   * Counts incidents whose work is finished.
   *
   * @param incidents incidents to inspect
   * @return number of RESOLVED or CLOSED incidents
   */
  public static long completed(List<Incident> incidents) {
    return incidents.stream()
        .filter(i -> i.status == Status.RESOLVED || i.status == Status.CLOSED)
        .count();
  }
}
