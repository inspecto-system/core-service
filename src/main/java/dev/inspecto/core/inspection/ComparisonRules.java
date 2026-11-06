package dev.inspecto.core.inspection;

import java.util.Optional;

/**
 * Classification of a defect when inspection A is compared with a later inspection B (ADR-004,
 * table of {@code GET /inspections/compare} in platform/api/core.yaml 0.6.0).
 */
public final class ComparisonRules {

  private ComparisonRules() {}

  /** State of a compared defect; {@code reopened} only for a NEW defect RESOLVED as of A. */
  public record Classification(ComparisonState state, boolean reopened) {}

  /**
   * Classifies a defect.
   *
   * @param asOfA status as of A, or null without observations up to A
   * @param asOfB status as of B, or null without observations up to B
   * @param checkedInB whether the defect was observed in B itself
   * @return the classification, or empty when the defect is not listed
   */
  public static Optional<Classification> classify(
      DefectStatus asOfA, DefectStatus asOfB, boolean checkedInB) {
    Classification result = null;
    if (asOfB == DefectStatus.RESOLVED) {
      // OPEN -> RESOLVED, or found and resolved after A; RESOLVED as of both is no change.
      if (asOfA != DefectStatus.RESOLVED) {
        result = new Classification(ComparisonState.RESOLVED, false);
      }
    } else if (asOfB == DefectStatus.OPEN) {
      if (!checkedInB) {
        result = new Classification(ComparisonState.NOT_CHECKED, false);
      } else if (asOfA == DefectStatus.OPEN) {
        result = new Classification(ComparisonState.REMAINING, false);
      } else {
        result = new Classification(ComparisonState.NEW, asOfA == DefectStatus.RESOLVED);
      }
    }
    return Optional.ofNullable(result);
  }
}
