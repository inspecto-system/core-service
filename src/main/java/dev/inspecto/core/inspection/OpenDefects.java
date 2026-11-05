package dev.inspecto.core.inspection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Defects to re-check during a repeat inspection: found in an earlier inspection and still {@code
 * OPEN} according to the latest observation before the given inspection.
 */
@Component
public class OpenDefects {

  /** Order of inspections in time; the same order decides the current status of a defect. */
  static final Comparator<Inspection> CHRONOLOGICAL =
      Comparator.comparing((Inspection i) -> i.fields().inspectedAt())
          .thenComparing(Inspection::getCreatedAt)
          .thenComparing(Inspection::getId);

  private final InspectionRepository inspections;
  private final DefectRepository defects;
  private final ObservationRepository observations;

  /** Creates the component. */
  public OpenDefects(
      InspectionRepository inspections,
      DefectRepository defects,
      ObservationRepository observations) {
    this.inspections = inspections;
    this.defects = defects;
    this.observations = observations;
  }

  /** Open defects of the inspection's property as they stood before the inspection. */
  public List<OpenDefect> before(Inspection target) {
    UUID propertyId = target.getPropertyId();
    Map<UUID, Inspection> byId =
        inspections.findAllByPropertyId(propertyId).stream()
            .collect(Collectors.toMap(Inspection::getId, Function.identity()));
    Map<UUID, List<DefectObservation>> byDefect =
        observations.findByPropertyId(propertyId).stream()
            .collect(Collectors.groupingBy(DefectObservation::getDefectId));
    List<OpenDefect> result = new ArrayList<>();
    for (Defect defect : defects.findByPropertyIdOrderByCreatedAtAscIdAsc(propertyId)) {
      List<DefectObservation> defectObservations = byDefect.getOrDefault(defect.getId(), List.of());
      latestBefore(target, defectObservations, byId)
          .filter(last -> last.getStatus() == DefectStatus.OPEN)
          .ifPresent(last ->
              result.add(new OpenDefect(defect, last, observationIn(target, defectObservations))));
    }
    return result;
  }

  private static Optional<DefectObservation> latestBefore(
      Inspection target, List<DefectObservation> defectObservations, Map<UUID, Inspection> byId) {
    return defectObservations.stream()
        .filter(o -> CHRONOLOGICAL.compare(byId.get(o.getInspectionId()), target) < 0)
        .max(Comparator.comparing(o -> byId.get(o.getInspectionId()), CHRONOLOGICAL));
  }

  private static DefectObservation observationIn(
      Inspection target, List<DefectObservation> defectObservations) {
    return defectObservations.stream()
        .filter(o -> o.getInspectionId().equals(target.getId()))
        .findFirst()
        .orElse(null);
  }

  /**
   * Defect to re-check.
   *
   * @param lastObservation latest observation before the inspection, {@code OPEN}
   * @param currentObservation observation in the inspection, {@code null} if not recorded yet
   */
  public record OpenDefect(
      Defect defect, DefectObservation lastObservation, DefectObservation currentObservation) {}
}
