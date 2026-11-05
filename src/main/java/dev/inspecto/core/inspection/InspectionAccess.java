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
  private final DefectRepository defects;
  private final ObservationRepository observations;

  /** Creates the component. */
  public InspectionAccess(
      PropertyAccess properties,
      InspectionRepository inspections,
      ChecklistItemRepository items,
      DefectRepository defects,
      ObservationRepository observations) {
    this.properties = properties;
    this.inspections = inspections;
    this.items = items;
    this.defects = defects;
    this.observations = observations;
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

  /**
   * Returns the defect if the user owns its property.
   *
   * @throws ApiException {@code NOT_FOUND} when the defect does not exist or is someone else's
   */
  public Defect ownedDefect(UUID defectId, UUID userId) {
    return defects.findOwned(defectId, userId).orElseThrow(() -> notFound("Defect not found"));
  }

  /**
   * Returns the observation if the user owns the property of its inspection.
   *
   * @throws ApiException {@code NOT_FOUND} when the observation does not exist or is someone else's
   */
  public DefectObservation ownedObservation(UUID observationId, UUID userId) {
    return observations
        .findOwned(observationId, userId)
        .orElseThrow(() -> notFound("Observation not found"));
  }

  /**
   * Inspection of an item the user was already given access to, locked against completion until the
   * end of the transaction. Must be the first load of the inspection in the transaction.
   */
  public Inspection inspectionForShare(UUID inspectionId) {
    return inspections
        .findForShare(inspectionId)
        .orElseThrow(() -> notFound("Inspection not found"));
  }

  static ApiException notFound(String detail) {
    return new ApiException(ErrorCode.NOT_FOUND, detail);
  }
}
