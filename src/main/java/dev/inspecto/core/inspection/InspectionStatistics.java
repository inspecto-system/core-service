package dev.inspecto.core.inspection;

import dev.inspecto.core.dictionary.CategoryUsage;
import dev.inspecto.core.property.PropertyActivity;
import dev.inspecto.core.property.PropertyActivityReader;
import dev.inspecto.core.property.RoomUsage;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Inspection data other modules need: counters of the property card and whether rooms and
 * categories are still referenced.
 */
@Component
public class InspectionStatistics implements PropertyActivityReader, RoomUsage, CategoryUsage {

  private final InspectionRepository inspections;
  private final ChecklistItemRepository items;
  private final DefectRepository defects;

  /** Creates the component. */
  public InspectionStatistics(
      InspectionRepository inspections, ChecklistItemRepository items, DefectRepository defects) {
    this.inspections = inspections;
    this.items = items;
    this.defects = defects;
  }

  @Override
  public PropertyActivity read(UUID propertyId) {
    int inspectionCount = Math.toIntExact(inspections.countByPropertyId(propertyId));
    int openDefects =
        Math.toIntExact(defects.countByPropertyIdAndCurrentStatus(propertyId, DefectStatus.OPEN));
    return new PropertyActivity(
        inspectionCount, openDefects, inspections.lastInspectedAt(propertyId));
  }

  /** A room is used when an inspection checklist or a defect refers to it. */
  @Override
  public boolean isRoomUsed(UUID roomId) {
    return items.existsByRoomId(roomId) || defects.existsByRoomId(roomId);
  }

  /** A category is used when a defect has it. */
  @Override
  public boolean isCategoryUsed(UUID categoryId) {
    return defects.existsByCategoryId(categoryId);
  }
}
