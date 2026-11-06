package dev.inspecto.core.inspection;

/** State of a defect in a comparison of two inspections (ADR-004, common.yaml ComparisonState). */
public enum ComparisonState {
  NEW,
  RESOLVED,
  REMAINING,
  NOT_CHECKED
}
