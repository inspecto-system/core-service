package dev.inspecto.core.inspection;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.property.PropertyAccess;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Access to inspections and checklist items, following the rules of {@link PropertyAccess}: only
 * the owner of the property has access, and resources of other users are reported as missing.
 */
@Component
public class InspectionAccess {

  private final PropertyAccess properties;
  private final InspectionRepository inspections;
  private final ChecklistItemRepository items;

  /** Creates the component. */
  public InspectionAccess(
      PropertyAccess properties, InspectionRepository inspections, ChecklistItemRepository items) {
    this.properties = properties;
    this.inspections = inspections;
    this.items = items;
  }

  /**
   * Checks that the user owns the property.
   *
   * @throws ApiException {@code NOT_FOUND} when the property does not exist or is someone else's
   */
  public void requireOwnedProperty(UUID propertyId, UUID userId) {
    properties.ownedProperty(propertyId, userId);
  }

  /**
   * Returns the inspection if the user owns its property.
   *
   * @throws ApiException {@code NOT_FOUND} when the inspection does not exist or is someone else's
   */
  public Inspection ownedInspection(UUID inspectionId, UUID userId) {
    return inspections
        .findOwned(inspectionId, userId)
        .orElseThrow(() -> notFound("Inspection not found"));
  }

  /**
   * Same as {@link #ownedInspection}, locking the inspection against completion until the end of
   * the transaction.
   */
  public Inspection ownedInspectionForShare(UUID inspectionId, UUID userId) {
    return inspections
        .findOwnedForShare(inspectionId, userId)
        .orElseThrow(() -> notFound("Inspection not found"));
  }

  /**
   * Returns the checklist item if the user owns the property of its inspection.
   *
   * @throws ApiException {@code NOT_FOUND} when the item does not exist or is someone else's
   */
  public ChecklistItem ownedChecklistItem(UUID itemId, UUID userId) {
    return items.findOwned(itemId, userId).orElseThrow(() -> notFound("Checklist item not found"));
  }

  static ApiException notFound(String detail) {
    return new ApiException(ErrorCode.NOT_FOUND, detail);
  }
}
