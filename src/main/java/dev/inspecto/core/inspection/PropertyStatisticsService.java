package dev.inspecto.core.inspection;

import dev.inspecto.core.dictionary.AppLocale;
import dev.inspecto.core.dictionary.DefectCategory;
import dev.inspecto.core.dictionary.DefectCategoryRepository;
import dev.inspecto.core.dictionary.Severity;
import dev.inspecto.core.property.Room;
import dev.inspecto.core.property.RoomRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Defect statistics of a property (T-302, {@code GET /properties/{id}/statistics}). */
@Service
public class PropertyStatisticsService {

  private final InspectionAccess access;
  private final PropertyHistories histories;
  private final RoomRepository rooms;
  private final DefectCategoryRepository categories;

  /** Creates the service. */
  public PropertyStatisticsService(
      InspectionAccess access,
      PropertyHistories histories,
      RoomRepository rooms,
      DefectCategoryRepository categories) {
    this.access = access;
    this.histories = histories;
    this.rooms = rooms;
    this.categories = categories;
  }

  /** Statistics of an own property; groups count the defects open now. */
  @Transactional(readOnly = true)
  public PropertyStatistics of(UUID propertyId, UUID userId, AppLocale locale) {
    access.requireOwnedProperty(propertyId, userId);
    PropertyHistory history = histories.of(propertyId);
    List<Defect> open =
        history.defects().stream().filter(d -> d.getCurrentStatus() == DefectStatus.OPEN).toList();
    Map<UUID, String> roomNames =
        rooms.findByPropertyIdOrderBySortOrderAscIdAsc(propertyId).stream()
            .collect(Collectors.toMap(Room::getId, Room::getName));
    Map<UUID, String> categoryNames =
        categories.findVisible(userId).stream()
            .collect(Collectors.toMap(DefectCategory::getId, c -> c.getNames().in(locale)));
    List<InspectionDefectCount> byInspection =
        history.inspections().stream()
            .map(i ->
                new InspectionDefectCount(
                    i.getId(), i.fields().inspectedAt(), i.getStatus(), openAsOf(history, i)))
            .toList();
    return new PropertyStatistics(
        history.defects().size(),
        open.size(),
        history.defects().size() - open.size(),
        bySeverity(open, locale),
        grouped(open, d -> d.fields().categoryId(), categoryNames),
        grouped(open, Defect::getRoomId, roomNames),
        byInspection);
  }

  private static int openAsOf(PropertyHistory history, Inspection inspection) {
    return (int)
        history.defects().stream()
            .filter(d -> history.statusAsOf(d, inspection).orElse(null) == DefectStatus.OPEN)
            .count();
  }

  /** Every severity, so that charts keep the scale; the most severe first among equal counts. */
  private static List<CountByKey> bySeverity(List<Defect> open, AppLocale locale) {
    Map<Severity, Long> counts =
        open.stream()
            .collect(Collectors.groupingBy(d -> d.fields().severity(), Collectors.counting()));
    return Arrays.stream(Severity.values())
        .sorted(Comparator.comparingInt(Severity::rank).reversed())
        .map(s -> new CountByKey(s.name(), s.name(locale), counts.getOrDefault(s, 0L).intValue()))
        .sorted(Comparator.comparingInt(CountByKey::count).reversed())
        .toList();
  }

  /** Groups with at least one open defect, largest first, then by label. */
  private static List<CountByKey> grouped(
      List<Defect> open, Function<Defect, UUID> key, Map<UUID, String> labels) {
    return open.stream()
        .collect(Collectors.groupingBy(key, Collectors.counting()))
        .entrySet()
        .stream()
        .map(e ->
            new CountByKey(
                e.getKey().toString(),
                labels.getOrDefault(e.getKey(), ""),
                e.getValue().intValue()))
        .sorted(
            Comparator.comparingInt(CountByKey::count)
                .reversed()
                .thenComparing(CountByKey::label)
                .thenComparing(CountByKey::key))
        .toList();
  }

  /** Response of {@code GET /properties/{id}/statistics} (PropertyStatistics in core.yaml). */
  public record PropertyStatistics(
      int totalDefects,
      int openDefects,
      int resolvedDefects,
      List<CountByKey> bySeverity,
      List<CountByKey> byCategory,
      List<CountByKey> byRoom,
      List<InspectionDefectCount> byInspection) {

    /** Defensive copies: records are part of the API. */
    public PropertyStatistics {
      bySeverity = List.copyOf(bySeverity);
      byCategory = List.copyOf(byCategory);
      byRoom = List.copyOf(byRoom);
      byInspection = List.copyOf(byInspection);
    }
  }

  /**
   * Number of open defects of a group; {@code label} is its name in the language of the request.
   */
  public record CountByKey(String key, String label, int count) {}

  /** Defects open as of an inspection (latest observation in it or earlier). */
  public record InspectionDefectCount(
      UUID inspectionId, Instant inspectedAt, InspectionStatus status, int openDefects) {}
}
