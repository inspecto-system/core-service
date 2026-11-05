package dev.inspecto.core.inspection;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.common.error.FieldViolation;
import dev.inspecto.core.dictionary.DefectCategory;
import dev.inspecto.core.dictionary.DefectCategoryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Checks the identifiers a client refers to when it records defects. Unknown identifiers and
 * identifiers of other users' resources are reported the same way, as invalid fields.
 */
@Component
public class DefectReferences {

  private static final String UNKNOWN_ID = "UNKNOWN_ID";

  private final ChecklistRooms checklistRooms;
  private final DefectCategoryRepository categories;
  private final ChecklistItemRepository items;
  private final DefectRepository defects;

  /** Creates the component. */
  public DefectReferences(
      ChecklistRooms checklistRooms,
      DefectCategoryRepository categories,
      ChecklistItemRepository items,
      DefectRepository defects) {
    this.checklistRooms = checklistRooms;
    this.categories = categories;
    this.items = items;
    this.defects = defects;
  }

  /**
   * Checks that the room belongs to the property.
   *
   * @throws ApiException {@code VALIDATION_ERROR} on field {@code roomId} otherwise
   */
  public void requireRoom(UUID propertyId, UUID roomId) {
    checklistRooms.requireRoomOf(propertyId, roomId);
  }

  /**
   * Checks that the category is a system category or one of the user's own, and is active.
   *
   * @throws ApiException {@code VALIDATION_ERROR} on field {@code categoryId} otherwise
   */
  public void requireUsableCategory(UUID categoryId, UUID userId) {
    DefectCategory category =
        categories
            .findById(categoryId)
            .filter(c -> c.isVisibleTo(userId))
            .orElseThrow(() -> invalid("categoryId", UNKNOWN_ID));
    if (!category.isActive()) {
      throw invalid("categoryId", "INACTIVE");
    }
  }

  /**
   * Checks that the optional checklist item is an item of the inspection in the given room.
   *
   * @throws ApiException {@code VALIDATION_ERROR} on field {@code checklistItemId} otherwise
   */
  public void requireChecklistItem(UUID itemId, UUID inspectionId, UUID roomId) {
    if (itemId == null) {
      return;
    }
    boolean matches =
        items
            .findById(itemId)
            .filter(item -> item.getInspectionId().equals(inspectionId))
            .filter(item -> item.getRoomId().equals(roomId))
            .isPresent();
    if (!matches) {
      throw invalid("checklistItemId", UNKNOWN_ID);
    }
  }

  /**
   * Returns the defect if it belongs to the property.
   *
   * @throws ApiException {@code VALIDATION_ERROR} on field {@code defectId} otherwise
   */
  public Defect defectOf(UUID propertyId, UUID defectId) {
    return defects
        .findById(defectId)
        .filter(defect -> defect.getPropertyId().equals(propertyId))
        .orElseThrow(() -> invalid("defectId", UNKNOWN_ID));
  }

  private static ApiException invalid(String field, String code) {
    return new ApiException(
        ErrorCode.VALIDATION_ERROR,
        "Request contains invalid fields",
        List.of(new FieldViolation(field, code)));
  }
}
