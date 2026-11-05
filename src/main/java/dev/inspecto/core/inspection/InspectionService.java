package dev.inspecto.core.inspection;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.OptimisticVersion;
import dev.inspecto.core.common.id.UuidV7;
import dev.inspecto.core.dictionary.AppLocale;
import dev.inspecto.core.inspection.ChecklistRooms.ChecklistSelection;
import dev.inspecto.core.inspection.ChecklistRooms.RoomChecklist;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inspections of the user's properties (T-201). */
@Service
public class InspectionService {

  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc("inspectedAt"), Sort.Order.desc("id"));

  private final InspectionAccess access;
  private final InspectionRepository inspections;
  private final ChecklistItemRepository items;
  private final ChecklistRooms checklistRooms;
  private final Clock clock;

  /** Creates the service. */
  public InspectionService(
      InspectionAccess access,
      InspectionRepository inspections,
      ChecklistItemRepository items,
      ChecklistRooms checklistRooms,
      Clock clock) {
    this.access = access;
    this.inspections = inspections;
    this.items = items;
    this.checklistRooms = checklistRooms;
    this.clock = clock;
  }

  /** Inspections of an own property, the latest inspection date first. */
  @Transactional(readOnly = true)
  public Page<Inspection> list(UUID propertyId, UUID userId, int page, int size) {
    access.requireOwnedProperty(propertyId, userId);
    return inspections.findByPropertyId(propertyId, PageRequest.of(page, size, NEWEST_FIRST));
  }

  /**
   * Creates a draft inspection with its checklist in one transaction.
   *
   * @throws ApiException {@code VALIDATION_ERROR} when a room or a template is unknown
   */
  @Transactional
  public InspectionView create(
      UUID propertyId, UUID userId, NewInspection request, AppLocale locale) {
    access.requireOwnedProperty(propertyId, userId);
    List<ChecklistItem.ChecklistItemDraft> drafts =
        checklistRooms.plan(propertyId, userId, request.selection(), locale);
    Instant now = clock.instant();
    Inspection inspection =
        inspections.save(
            new Inspection(UuidV7.generate(clock), propertyId, userId, request.fields(), now));
    List<ChecklistItem> checklist =
        items.saveAll(
            drafts.stream()
                .map(draft ->
                    new ChecklistItem(UuidV7.generate(clock), inspection.getId(), draft, now))
                .toList());
    return new InspectionView(inspection, checklistRooms.arrange(checklist));
  }

  /** Inspection with its checklist grouped by room. */
  @Transactional(readOnly = true)
  public InspectionView get(UUID inspectionId, UUID userId) {
    Inspection inspection = access.ownedInspection(inspectionId, userId);
    List<ChecklistItem> checklist = items.findByInspectionIdOrderBySortOrderAscIdAsc(inspectionId);
    return new InspectionView(inspection, checklistRooms.arrange(checklist));
  }

  /**
   * Changes the descriptive fields and optionally starts the inspection.
   *
   * @throws ApiException {@code VERSION_CONFLICT} for a stale version, {@code
   *     INVALID_STATE_TRANSITION} for a completed inspection
   */
  @Transactional
  public Inspection update(UUID inspectionId, UUID userId, InspectionUpdate update) {
    Inspection inspection = access.ownedInspection(inspectionId, userId);
    OptimisticVersion.require(inspection.getVersion(), update.expectedVersion());
    inspection.update(update.fields().apply(inspection.fields()), update.start(), clock.instant());
    return inspections.saveAndFlush(inspection);
  }

  /**
   * Completes an inspection in progress.
   *
   * @throws ApiException {@code VERSION_CONFLICT} for a stale version, {@code
   *     INVALID_STATE_TRANSITION} unless the inspection is in progress
   */
  @Transactional
  public Inspection complete(UUID inspectionId, UUID userId, long expectedVersion) {
    Inspection inspection = access.ownedInspection(inspectionId, userId);
    OptimisticVersion.require(inspection.getVersion(), expectedVersion);
    inspection.complete(clock.instant());
    return inspections.saveAndFlush(inspection);
  }

  /**
   * Deletes a draft inspection with its checklist.
   *
   * @throws ApiException {@code INVALID_STATE_TRANSITION} unless the inspection is a draft
   */
  @Transactional
  public void delete(UUID inspectionId, UUID userId) {
    Inspection inspection = access.ownedInspection(inspectionId, userId);
    inspection.requireDeletable();
    inspections.delete(inspection);
  }

  /** Fields of a new inspection with the rooms and templates of its checklist. */
  public record NewInspection(InspectionFields fields, ChecklistSelection selection) {}

  /**
   * Requested change of an inspection.
   *
   * @param fields computes the new descriptive fields from the current ones
   * @param start whether to start a draft
   * @param expectedVersion version the client based the change on
   */
  public record InspectionUpdate(
      UnaryOperator<InspectionFields> fields, boolean start, long expectedVersion) {}

  /** Inspection with its checklist grouped by room. */
  public record InspectionView(Inspection inspection, List<RoomChecklist> rooms) {

    /** Copies the room list. */
    public InspectionView {
      rooms = List.copyOf(rooms);
    }
  }
}
