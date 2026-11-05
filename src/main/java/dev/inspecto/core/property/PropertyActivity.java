package dev.inspecto.core.property;

import java.time.Instant;

/**
 * Counters of the property card.
 *
 * @param inspectionCount inspections of the property in any status
 * @param openDefectCount defects whose last observation is {@code OPEN}
 * @param lastInspectionAt latest inspection date, {@code null} without inspections
 */
public record PropertyActivity(
    int inspectionCount, int openDefectCount, Instant lastInspectionAt) {}
