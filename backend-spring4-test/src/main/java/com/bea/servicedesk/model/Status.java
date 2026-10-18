package com.bea.servicedesk.model;

/** Workflow states an incident moves through from submission to closure. */
public enum Status {
  OPEN,
  ASSIGNED,
  IN_PROGRESS,
  ESCALATED,
  RESOLVED,
  CLOSED
}
