package dev.inspecto.core.inspection;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.common.error.FieldViolation;
import dev.inspecto.core.common.id.UuidV7;
import dev.inspecto.core.dictionary.AppLocale;
import dev.inspecto.core.dictionary.ChecklistTemplate;
import dev.inspecto.core.dictionary.ChecklistTemplateItem;
import dev.inspecto.core.dictionary.ChecklistTemplateRepository;
import dev.inspecto.core.inspection.ChecklistItem.ChecklistItemDraft;
import dev.inspecto.core.property.Room;
import dev.inspecto.core.property.RoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Connects checklists with the rooms of a property and with checklist templates (T-202). */
@Component
public class ChecklistRooms {

  private static final Comparator<Room> DISPLAY_ORDER =
      Comparator.comparingInt(Room::getSortOrder).thenComparing(Room::getId);

  private final RoomRepository rooms;
  private final ChecklistTemplateRepository templates;
  private final ChecklistItemRepository items;
  private final Clock clock;

  /** Creates the component. */
  public ChecklistRooms(
      RoomRepository rooms,
      ChecklistTemplateRepository templates,
      ChecklistItemRepository items,
      Clock clock) {
    this.rooms = rooms;
    this.templates = templates;
    this.items = items;
    this.clock = clock;
  }

  /**
   * Creates the checklist of a new inspection: every item of every selected template, in the order
   * the templates were given, for every selected room. Titles are copied in the requested language.
   *
   * @throws ApiException {@code VALIDATION_ERROR} when a room is not part of the property or a
   *     template is unknown to the user
   */
  public List<ChecklistItem> createChecklist(
      Inspection inspection, UUID userId, ChecklistSelection selection, AppLocale locale) {
    List<Room> selectedRooms = roomsOf(inspection.getPropertyId(), selection.roomIds());
    List<ChecklistTemplateItem> templateItems =
        visibleTemplates(userId, selection.templateCodes()).stream()
            .flatMap(template -> template.getItems().stream())
            .toList();
    Instant now = clock.instant();
    List<ChecklistItem> checklist = new ArrayList<>();
    for (Room room : selectedRooms) {
      for (int sortOrder = 0; sortOrder < templateItems.size(); sortOrder++) {
        ChecklistTemplateItem item = templateItems.get(sortOrder);
        checklist.add(
            new ChecklistItem(
                UuidV7.generate(clock),
                inspection.getId(),
                new ChecklistItemDraft(
                    room.getId(),
                    item.getId(),
                    item.getElementGroup(),
                    item.getTitles().in(locale),
                    sortOrder),
                now));
      }
    }
    return items.saveAll(checklist);
  }

  /**
   * Checks that the room belongs to the property.
   *
   * @throws ApiException {@code VALIDATION_ERROR} on field {@code roomId} otherwise
   */
  public void requireRoomOf(UUID propertyId, UUID roomId) {
    boolean belongs =
        rooms.findById(roomId).filter(room -> room.getPropertyId().equals(propertyId)).isPresent();
    if (!belongs) {
      throw invalid("roomId", "UNKNOWN_ID");
    }
  }

  private List<Room> roomsOf(UUID propertyId, List<UUID> roomIds) {
    Set<UUID> wanted = new LinkedHashSet<>(roomIds);
    List<Room> found =
        rooms.findAllById(wanted).stream()
            .filter(room -> room.getPropertyId().equals(propertyId))
            .sorted(DISPLAY_ORDER)
            .toList();
    if (found.size() != wanted.size()) {
      throw invalid("roomIds", "UNKNOWN_ID");
    }
    return found;
  }

  private List<ChecklistTemplate> visibleTemplates(UUID userId, List<String> codes) {
    Map<String, ChecklistTemplate> byCode =
        templates.findVisible(userId).stream()
            .collect(Collectors.toMap(ChecklistTemplate::getCode, Function.identity()));
    List<ChecklistTemplate> selected = new ArrayList<>();
    for (String code : new LinkedHashSet<>(codes)) {
      ChecklistTemplate template = byCode.get(code);
      if (template == null) {
        throw invalid("templateCodes", "UNKNOWN_CODE");
      }
      selected.add(template);
    }
    return selected;
  }

  private static ApiException invalid(String field, String code) {
    return new ApiException(
        ErrorCode.VALIDATION_ERROR,
        "Request contains invalid fields",
        List.of(new FieldViolation(field, code)));
  }

  /** Rooms and templates chosen for a new inspection. */
  public record ChecklistSelection(List<UUID> roomIds, List<String> templateCodes) {

    /** Copies the lists. */
    public ChecklistSelection {
      roomIds = List.copyOf(roomIds);
      templateCodes = List.copyOf(templateCodes);
    }
  }
}
