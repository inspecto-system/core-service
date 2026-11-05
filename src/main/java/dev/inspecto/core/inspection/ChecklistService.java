package dev.inspecto.core.inspection;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.OptimisticVersion;
import dev.inspecto.core.common.id.UuidV7;
import dev.inspecto.core.dictionary.ElementGroup;
import dev.inspecto.core.inspection.ChecklistItem.ChecklistEntry;
import dev.inspecto.core.inspection.ChecklistItem.ChecklistItemDraft;
import java.time.Clock;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checklist of an inspection (T-202). Every change locks the inspection with {@code FOR SHARE}, so
 * a concurrent completion waits for it and the checklist of a completed inspection stays as it was.
 */
@Service
public class ChecklistService {

  private final InspectionAccess access;
  private final ChecklistItemRepository items;
  private final ChecklistRooms checklistRooms;
  private final Clock clock;

  /** Creates the service. */
  public ChecklistService(
      InspectionAccess access,
      ChecklistItemRepository items,
      ChecklistRooms checklistRooms,
      Clock clock) {
    this.access = access;
    this.items = items;
    this.checklistRooms = checklistRooms;
    this.clock = clock;
  }

  /**
   * Adds a custom item after the existing items of the room.
   *
   * @throws ApiException {@code VALIDATION_ERROR} when the room is not part of the property, {@code
   *     INVALID_STATE_TRANSITION} when the inspection is completed
   */
  @Transactional
  public ChecklistItem addCustom(UUID inspectionId, UUID userId, CustomItem item) {
    Inspection inspection = access.ownedInspectionForShare(inspectionId, userId);
    inspection.requireNotCompleted();
    checklistRooms.requireRoomOf(inspection.getPropertyId(), item.roomId());
    int sortOrder = items.maxSortOrder(inspectionId, item.roomId()) + 1;
    ChecklistItemDraft draft =
        new ChecklistItemDraft(item.roomId(), null, item.elementGroup(), item.title(), sortOrder);
    return items.save(
        new ChecklistItem(UuidV7.generate(clock), inspectionId, draft, clock.instant()));
  }

  /**
   * Records the result and the comment of an item.
   *
   * @throws ApiException {@code VERSION_CONFLICT} for a stale version, {@code
   *     INVALID_STATE_TRANSITION} when the inspection is completed
   */
  @Transactional
  public ChecklistItem update(UUID itemId, UUID userId, ChecklistUpdate update) {
    ChecklistItem item = access.ownedChecklistItem(itemId, userId);
    access.inspectionForShare(item.getInspectionId()).requireNotCompleted();
    OptimisticVersion.require(item.getVersion(), update.expectedVersion());
    item.record(update.entry().apply(item.entry()), clock.instant());
    return items.saveAndFlush(item);
  }

  /** Custom item given by the inspector. */
  public record CustomItem(UUID roomId, ElementGroup elementGroup, String title) {}

  /**
   * Requested change of an item.
   *
   * @param entry computes the new result and comment from the current ones
   * @param expectedVersion version the client based the change on
   */
  public record ChecklistUpdate(UnaryOperator<ChecklistEntry> entry, long expectedVersion) {}
}
