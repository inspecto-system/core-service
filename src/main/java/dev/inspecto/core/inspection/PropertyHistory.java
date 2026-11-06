package dev.inspecto.core.inspection;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Inspections, defects and observations of one property, loaded once, answering "what was the
 * status of a defect as of an inspection": the status of its latest observation in that inspection
 * or an earlier one, in the order {@link OpenDefects#CHRONOLOGICAL} (ADR-004, contract 0.6.0).
 */
public final class PropertyHistory {

  private final List<Inspection> inspections;
  private final List<Defect> defects;
  private final Map<UUID, Inspection> inspectionsById;
  private final Map<UUID, List<DefectObservation>> observationsByDefect;

  /** Builds the history from the rows of one property. */
  public PropertyHistory(
      List<Inspection> inspections, List<Defect> defects, List<DefectObservation> observations) {
    this.inspections = List.copyOf(inspections.stream().sorted(OpenDefects.CHRONOLOGICAL).toList());
    this.defects = List.copyOf(defects);
    this.inspectionsById =
        inspections.stream().collect(Collectors.toMap(Inspection::getId, Function.identity()));
    this.observationsByDefect =
        observations.stream().collect(Collectors.groupingBy(DefectObservation::getDefectId));
  }

  /** Inspections of the property, earliest first. */
  public List<Inspection> inspections() {
    return inspections;
  }

  /** Defects of the property, oldest first. */
  public List<Defect> defects() {
    return defects;
  }

  /** Status as of the inspection, empty when the defect had no observation up to it. */
  public Optional<DefectStatus> statusAsOf(Defect defect, Inspection inspection) {
    return observationsOf(defect).stream()
        .filter(o -> OpenDefects.CHRONOLOGICAL.compare(inspectionOf(o), inspection) <= 0)
        .max(Comparator.comparing(this::inspectionOf, OpenDefects.CHRONOLOGICAL))
        .map(DefectObservation::getStatus);
  }

  /** Whether the defect was observed in the inspection itself. */
  public boolean checkedIn(Defect defect, Inspection inspection) {
    return observationsOf(defect).stream()
        .anyMatch(o -> o.getInspectionId().equals(inspection.getId()));
  }

  private List<DefectObservation> observationsOf(Defect defect) {
    return observationsByDefect.getOrDefault(defect.getId(), List.of());
  }

  private Inspection inspectionOf(DefectObservation observation) {
    return inspectionsById.get(observation.getInspectionId());
  }
}
