package dev.inspecto.core.inspection;

import dev.inspecto.core.inspection.DefectRepository.InspectionCount;
import dev.inspecto.core.property.Room;
import dev.inspecto.core.property.RoomRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Read models of inspections: checklist and observations by room, defect counters. */
@Component
public class InspectionViews {

  private static final Comparator<Room> ROOM_ORDER =
      Comparator.comparingInt(Room::getSortOrder).thenComparing(Room::getId);
  private static final Comparator<ChecklistItem> ITEM_ORDER =
      Comparator.comparingInt(ChecklistItem::getSortOrder).thenComparing(ChecklistItem::getId);

  private final ChecklistItemRepository items;
  private final ObservationRepository observations;
  private final DefectRepository defects;
  private final RoomRepository rooms;
  private final DefectJournal journal;

  /** Creates the component. */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring injects shared singleton beans; copying them is meaningless")
  public InspectionViews(
      ChecklistItemRepository items,
      ObservationRepository observations,
      DefectRepository defects,
      RoomRepository rooms,
      DefectJournal journal) {
    this.items = items;
    this.observations = observations;
    this.defects = defects;
    this.rooms = rooms;
    this.journal = journal;
  }

  /** Inspection with its stored checklist and observations. */
  public InspectionView of(Inspection inspection) {
    return of(inspection, items.findByInspectionIdOrderBySortOrderAscIdAsc(inspection.getId()));
  }

  /**
   * Inspection with the given checklist and its observations, grouped by room. A room appears when
   * it has checklist items or observed defects; rooms are in display order.
   */
  public InspectionView of(Inspection inspection, List<ChecklistItem> checklist) {
    List<ObservationView> observed =
        journal.withPhotos(
            observations.findByInspectionIdOrderByCreatedAtAscIdAsc(inspection.getId()));
    Map<UUID, Defect> defectById =
        defects
            .findAllById(observed.stream().map(o -> o.observation().getDefectId()).toList())
            .stream()
            .collect(Collectors.toMap(Defect::getId, Function.identity()));
    Map<UUID, List<ChecklistItem>> itemsByRoom =
        checklist.stream()
            .sorted(ITEM_ORDER)
            .collect(
                Collectors.groupingBy(
                    ChecklistItem::getRoomId, LinkedHashMap::new, Collectors.toList()));
    Map<UUID, List<ObservedDefect>> observedByRoom =
        observed.stream()
            .map(o -> new ObservedDefect(o, defectById.get(o.observation().getDefectId())))
            .collect(Collectors.groupingBy(d -> d.defect().getRoomId()));
    Set<UUID> roomIds = new LinkedHashSet<>(itemsByRoom.keySet());
    roomIds.addAll(observedByRoom.keySet());
    List<RoomChecklist> roomViews =
        rooms.findAllById(roomIds).stream()
            .sorted(ROOM_ORDER)
            .map(room ->
                new RoomChecklist(
                    room,
                    itemsByRoom.getOrDefault(room.getId(), List.of()),
                    observedByRoom.getOrDefault(room.getId(), List.of())))
            .toList();
    return new InspectionView(inspection, roomViews);
  }

  /** Inspections with the number of open observations and of defects first found there. */
  public List<InspectionSummary> summaries(List<Inspection> page) {
    List<UUID> ids = page.stream().map(Inspection::getId).toList();
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<UUID, Long> open = byInspection(observations.countOpenIn(ids));
    Map<UUID, Long> found = byInspection(defects.countFoundIn(ids));
    return page.stream()
        .map(i ->
            new InspectionSummary(
                i,
                Math.toIntExact(open.getOrDefault(i.getId(), 0L)),
                Math.toIntExact(found.getOrDefault(i.getId(), 0L))))
        .toList();
  }

  private static Map<UUID, Long> byInspection(List<InspectionCount> counts) {
    return counts.stream()
        .collect(Collectors.toMap(InspectionCount::getInspectionId, InspectionCount::getTotal));
  }

  /** Inspection with its checklist and observations grouped by room. */
  public record InspectionView(Inspection inspection, List<RoomChecklist> rooms) {

    /** Copies the room list. */
    public InspectionView {
      rooms = List.copyOf(rooms);
    }
  }

  /** Room of an inspection with its checklist and the defects observed there. */
  public record RoomChecklist(
      Room room, List<ChecklistItem> items, List<ObservedDefect> observations) {

    /** Copies the lists. */
    public RoomChecklist {
      items = List.copyOf(items);
      observations = List.copyOf(observations);
    }
  }

  /** Observation together with its defect. */
  public record ObservedDefect(ObservationView observation, Defect defect) {}

  /**
   * Inspection in the history of a property.
   *
   * @param defectCount observations with status {@code OPEN} in the inspection
   * @param newDefectCount defects first found in the inspection
   */
  public record InspectionSummary(Inspection inspection, int defectCount, int newDefectCount) {}
}
