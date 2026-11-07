package dev.inspecto.core.inspection;

import dev.inspecto.core.dictionary.AppLocale;
import dev.inspecto.core.dictionary.Severity;
import dev.inspecto.core.inspection.ComparisonDtos.InspectionComparisonResponse;
import dev.inspecto.core.inspection.DefectDtos.DefectResponse;
import dev.inspecto.core.inspection.DefectDtos.ObservationResponse;
import dev.inspecto.core.inspection.InspectionDtos.ChecklistItemResponse;
import dev.inspecto.core.inspection.InspectionDtos.InspectionResponse;
import dev.inspecto.core.inspection.InspectionViews.InspectionView;
import dev.inspecto.core.inspection.InspectionViews.ObservedDefect;
import dev.inspecto.core.inspection.InspectionViews.RoomChecklist;
import dev.inspecto.core.inspection.ReportDataDtos.Inspector;
import dev.inspecto.core.inspection.ReportDataDtos.ReportDataResponse;
import dev.inspecto.core.inspection.ReportDataDtos.ReportDefect;
import dev.inspecto.core.inspection.ReportDataDtos.ReportRoom;
import dev.inspecto.core.inspection.ReportDataDtos.ReportSummary;
import dev.inspecto.core.property.Property;
import dev.inspecto.core.property.PropertyDtos.PropertyResponse;
import dev.inspecto.core.property.PropertyDtos.RoomResponse;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregate of an inspection for its PDF report (T-402, {@code GET
 * /internal/v1/inspections/{id}/report-data}). The caller is report-service with scope {@code
 * reports.data}; it has checked the user's access to the property before asking.
 */
@Service
public class ReportDataService {

  private final InspectionRepository inspections;
  private final InspectionViews views;
  private final ReportContext context;
  private final InspectionComparisonService comparison;
  private final Clock clock;

  /** Creates the service. */
  public ReportDataService(
      InspectionRepository inspections,
      InspectionViews views,
      ReportContext context,
      InspectionComparisonService comparison,
      Clock clock) {
    this.inspections = inspections;
    this.views = views;
    this.context = context;
    this.comparison = comparison;
    this.clock = clock;
  }

  /**
   * Report data of the inspection in the given language.
   *
   * @throws dev.inspecto.core.common.error.ApiException {@code NOT_FOUND} for an unknown inspection
   */
  @Transactional(readOnly = true)
  public ReportDataResponse of(UUID inspectionId, AppLocale locale) {
    Inspection inspection = inspections.findById(inspectionId).orElseThrow(ReportContext::notFound);
    Property property = context.property(inspection);
    Map<UUID, String> categoryNames = context.categoryNames(property.getOwnerId(), locale);
    InspectionView view = views.of(inspection);
    List<ReportRoom> rooms =
        view.rooms().stream().map(room -> reportRoom(room, categoryNames)).toList();
    return new ReportDataResponse(
        PropertyResponse.of(property),
        InspectionResponse.of(inspection),
        new Inspector(context.inspectorName(inspection)),
        rooms,
        summary(view),
        previousComparison(inspection, property, locale),
        clock.instant());
  }

  private static ReportRoom reportRoom(RoomChecklist room, Map<UUID, String> categoryNames) {
    return new ReportRoom(
        RoomResponse.of(room.room()),
        room.items().stream().map(ChecklistItemResponse::of).toList(),
        room.observations().stream().map(o -> reportDefect(o, categoryNames)).toList());
  }

  private static ReportDefect reportDefect(ObservedDefect observed, Map<UUID, String> names) {
    Defect defect = observed.defect();
    return new ReportDefect(
        DefectResponse.of(defect),
        names.getOrDefault(defect.fields().categoryId(), ""),
        ObservationResponse.of(observed.observation()));
  }

  /** Defects found open in this inspection, by the severity recorded in it. */
  private static ReportSummary summary(InspectionView view) {
    Map<Severity, Integer> bySeverity = new EnumMap<>(Severity.class);
    for (Severity severity : Severity.values()) {
      bySeverity.put(severity, 0);
    }
    int total = 0;
    for (RoomChecklist room : view.rooms()) {
      for (ObservedDefect observed : room.observations()) {
        DefectObservation observation = observed.observation().observation();
        if (observation.getStatus() == DefectStatus.OPEN) {
          bySeverity.merge(observation.entry().severity(), 1, Integer::sum);
          total++;
        }
      }
    }
    return new ReportSummary(total, bySeverity);
  }

  /** Comparison with the previous inspection of the property, or null for the first one. */
  private InspectionComparisonResponse previousComparison(
      Inspection inspection, Property property, AppLocale locale) {
    return inspections.findAllByPropertyId(property.getId()).stream()
        .filter(i -> OpenDefects.CHRONOLOGICAL.compare(i, inspection) < 0)
        .max(OpenDefects.CHRONOLOGICAL)
        .map(previous ->
            comparison.compare(previous.getId(), inspection.getId(), property.getOwnerId(), locale))
        .orElse(null);
  }
}
