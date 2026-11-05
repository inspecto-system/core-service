package dev.inspecto.core.inspection;

/**
 * Status of a defect in an observation, and the current status of a defect, which is the status of
 * its latest observation (common.yaml, ObservationStatus and DefectStatus). {@code RESOLVED} is
 * only set by an explicit observation (ADR-004).
 */
public enum DefectStatus {
  OPEN,
  RESOLVED
}
