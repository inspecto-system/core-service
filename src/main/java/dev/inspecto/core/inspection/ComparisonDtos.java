package dev.inspecto.core.inspection;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.inspecto.core.dictionary.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Response of {@code GET /inspections/compare} (platform/api/core.yaml, InspectionComparison). */
public final class ComparisonDtos {

  private ComparisonDtos() {}

  /** Comparison of inspection A ({@code base}) with the later inspection B ({@code target}). */
  public record InspectionComparisonResponse(
      ComparedInspection base,
      ComparedInspection target,
      StateCounts summary,
      List<ComparedDefect> defects,
      Map<Severity, StateCounts> bySeverity,
      List<CategoryStateCounts> byCategory) {

    /** Defensive copies: records are part of the API, not of the domain. */
    public InspectionComparisonResponse {
      defects = List.copyOf(defects);
      bySeverity = Map.copyOf(bySeverity);
      byCategory = List.copyOf(byCategory);
    }
  }

  /** Identity and time of a compared inspection. */
  public record ComparedInspection(UUID id, Instant inspectedAt) {}

  /** One defect of the comparison. */
  public record ComparedDefect(
      UUID defectId,
      ComparisonState state,
      boolean reopened,
      String title,
      Severity severity,
      UUID roomId,
      String roomName,
      UUID categoryId) {}

  /** Number of defects in each state. */
  public record StateCounts(
      @JsonProperty("new") int newCount, int resolved, int remaining, int notChecked) {

    static final StateCounts NONE = new StateCounts(0, 0, 0, 0);

    /** Counts with one more defect in the state. */
    StateCounts plus(ComparisonState state) {
      return switch (state) {
        case NEW -> new StateCounts(newCount + 1, resolved, remaining, notChecked);
        case RESOLVED -> new StateCounts(newCount, resolved + 1, remaining, notChecked);
        case REMAINING -> new StateCounts(newCount, resolved, remaining + 1, notChecked);
        case NOT_CHECKED -> new StateCounts(newCount, resolved, remaining, notChecked + 1);
      };
    }

    int total() {
      return newCount + resolved + remaining + notChecked;
    }
  }

  /** Counts of one category with its name in the language of the request. */
  public record CategoryStateCounts(
      UUID categoryId,
      String categoryName,
      @JsonProperty("new") int newCount,
      int resolved,
      int remaining,
      int notChecked) {

    static CategoryStateCounts of(UUID categoryId, String categoryName, StateCounts counts) {
      return new CategoryStateCounts(
          categoryId,
          categoryName,
          counts.newCount(),
          counts.resolved(),
          counts.remaining(),
          counts.notChecked());
    }

    int total() {
      return newCount + resolved + remaining + notChecked;
    }
  }
}
