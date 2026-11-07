package dev.inspecto.core.inspection;

import dev.inspecto.core.dictionary.Severity;
import dev.inspecto.core.inspection.ComparisonDtos.InspectionComparisonResponse;
import dev.inspecto.core.inspection.DefectDtos.DefectResponse;
import dev.inspecto.core.inspection.DefectDtos.ObservationResponse;
import dev.inspecto.core.inspection.InspectionDtos.ChecklistItemResponse;
import dev.inspecto.core.inspection.InspectionDtos.InspectionResponse;
import dev.inspecto.core.property.PropertyDtos.PropertyResponse;
import dev.inspecto.core.property.PropertyDtos.RoomResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Response of {@code GET /internal/v1/inspections/{id}/report-data} (core.yaml, ReportData). */
public final class ReportDataDtos {

  private ReportDataDtos() {}

  /** Everything report-service needs to render one inspection report. */
  public record ReportDataResponse(
      PropertyResponse property,
      InspectionResponse inspection,
      Inspector inspector,
      List<ReportRoom> rooms,
      ReportSummary summary,
      InspectionComparisonResponse comparison,
      Instant generatedAt) {

    /** Copies the room list. */
    public ReportDataResponse {
      rooms = List.copyOf(rooms);
    }
  }

  /** Person who conducted the inspection. */
  public record Inspector(String fullName) {}

  /** Room with its checklist and the defects observed in it during the inspection. */
  public record ReportRoom(
      RoomResponse room, List<ChecklistItemResponse> checklist, List<ReportDefect> defects) {

    /** Copies the lists. */
    public ReportRoom {
      checklist = List.copyOf(checklist);
      defects = List.copyOf(defects);
    }
  }

  /** Defect with its category name and its observation in this inspection (with photos). */
  public record ReportDefect(
      DefectResponse defect, String categoryName, ObservationResponse observation) {}

  /** Defects found open in the inspection: their number and the number for every severity. */
  public record ReportSummary(int total, Map<Severity, Integer> bySeverity) {

    /** Copies the map. */
    public ReportSummary {
      bySeverity = Map.copyOf(bySeverity);
    }
  }
}
