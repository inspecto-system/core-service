package dev.inspecto.core.inspection;

/** Life cycle of an inspection: {@code DRAFT → IN_PROGRESS → COMPLETED} (common.yaml). */
public enum InspectionStatus {
  DRAFT,
  IN_PROGRESS,
  COMPLETED
}
