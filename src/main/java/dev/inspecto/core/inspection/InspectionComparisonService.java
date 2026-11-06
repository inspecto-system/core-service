package dev.inspecto.core.inspection;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.common.error.FieldViolation;
import dev.inspecto.core.dictionary.AppLocale;
import dev.inspecto.core.dictionary.DefectCategory;
import dev.inspecto.core.dictionary.DefectCategoryRepository;
import dev.inspecto.core.dictionary.Severity;
import dev.inspecto.core.inspection.ComparisonDtos.CategoryStateCounts;
import dev.inspecto.core.inspection.ComparisonDtos.ComparedDefect;
import dev.inspecto.core.inspection.ComparisonDtos.ComparedInspection;
import dev.inspecto.core.inspection.ComparisonDtos.InspectionComparisonResponse;
import dev.inspecto.core.inspection.ComparisonDtos.StateCounts;
import dev.inspecto.core.inspection.ComparisonRules.Classification;
import dev.inspecto.core.property.Room;
import dev.inspecto.core.property.RoomRepository;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comparison of two inspections of a property (T-301, ADR-004). */
@Service
public class InspectionComparisonService {

  private final InspectionAccess access;
  private final PropertyHistories histories;
  private final RoomRepository rooms;
  private final DefectCategoryRepository categories;

  /** Creates the service. */
  public InspectionComparisonService(
      InspectionAccess access,
      PropertyHistories histories,
      RoomRepository rooms,
      DefectCategoryRepository categories) {
    this.access = access;
    this.histories = histories;
    this.rooms = rooms;
    this.categories = categories;
  }

  /**
   * Compares inspection A with the later inspection B of the same property.
   *
   * @throws ApiException {@code NOT_FOUND} when either inspection is not the user's, {@code
   *     VALIDATION_ERROR} when they belong to different properties or A is not earlier than B
   */
  @Transactional(readOnly = true)
  public InspectionComparisonResponse compare(
      UUID baseId, UUID targetId, UUID userId, AppLocale locale) {
    Inspection base = access.ownedInspection(baseId, userId);
    Inspection target = access.ownedInspection(targetId, userId);
    requireComparable(base, target);
    PropertyHistory history = histories.of(base.getPropertyId());
    Map<UUID, String> roomNames =
        rooms.findByPropertyIdOrderBySortOrderAscIdAsc(base.getPropertyId()).stream()
            .collect(Collectors.toMap(Room::getId, Room::getName));
    List<ComparedDefect> compared = new ArrayList<>();
    for (Defect defect : history.defects()) {
      ComparisonRules.classify(
              history.statusAsOf(defect, base).orElse(null),
              history.statusAsOf(defect, target).orElse(null),
              history.checkedIn(defect, target))
          .ifPresent(c -> compared.add(compared(defect, c, roomNames)));
    }
    return new InspectionComparisonResponse(
        new ComparedInspection(base.getId(), base.fields().inspectedAt()),
        new ComparedInspection(target.getId(), target.fields().inspectedAt()),
        count(compared),
        compared,
        bySeverity(compared),
        byCategory(compared, userId, locale));
  }

  private static void requireComparable(Inspection base, Inspection target) {
    String problem = null;
    if (!base.getPropertyId().equals(target.getPropertyId())) {
      problem = "The inspections belong to different properties";
    } else if (OpenDefects.CHRONOLOGICAL.compare(base, target) >= 0) {
      problem = "The base inspection must be earlier than the target one";
    }
    if (problem != null) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, problem, List.of(new FieldViolation("target", "INVALID")));
    }
  }

  private static ComparedDefect compared(
      Defect defect, Classification classification, Map<UUID, String> roomNames) {
    Defect.DefectFields fields = defect.fields();
    return new ComparedDefect(
        defect.getId(),
        classification.state(),
        classification.reopened(),
        fields.title(),
        fields.severity(),
        defect.getRoomId(),
        roomNames.getOrDefault(defect.getRoomId(), ""),
        fields.categoryId());
  }

  private static StateCounts count(List<ComparedDefect> defects) {
    StateCounts counts = StateCounts.NONE;
    for (ComparedDefect defect : defects) {
      counts = counts.plus(defect.state());
    }
    return counts;
  }

  /** Counts for every severity, also those without defects, so that charts keep their scale. */
  private static Map<Severity, StateCounts> bySeverity(List<ComparedDefect> defects) {
    Map<Severity, StateCounts> result = new EnumMap<>(Severity.class);
    for (Severity severity : Severity.values()) {
      result.put(severity, count(defects.stream().filter(d -> d.severity() == severity).toList()));
    }
    return result;
  }

  /** Categories with defects in the comparison, largest first, then by name. */
  private List<CategoryStateCounts> byCategory(
      List<ComparedDefect> defects, UUID userId, AppLocale locale) {
    Map<UUID, String> names =
        categories.findVisible(userId).stream()
            .collect(Collectors.toMap(DefectCategory::getId, c -> c.getNames().in(locale)));
    Map<UUID, List<ComparedDefect>> grouped =
        defects.stream()
            .collect(
                Collectors.groupingBy(
                    ComparedDefect::categoryId, LinkedHashMap::new, Collectors.toList()));
    Collator collator = Collator.getInstance(locale.toLocale());
    return grouped.entrySet().stream()
        .map(e ->
            CategoryStateCounts.of(
                e.getKey(), names.getOrDefault(e.getKey(), ""), count(e.getValue())))
        .sorted(
            Comparator.comparingInt(CategoryStateCounts::total)
                .reversed()
                .thenComparing(CategoryStateCounts::categoryName, collator))
        .toList();
  }
}
