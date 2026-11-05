package dev.inspecto.core.inspection;

import java.time.Instant;

/** Descriptive fields of an inspection; {@code title} and {@code notes} are optional. */
public record InspectionFields(String title, Instant inspectedAt, String notes) {}
