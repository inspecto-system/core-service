package dev.inspecto.core.inspection;

import dev.inspecto.core.property.PropertyActivity;
import dev.inspecto.core.property.PropertyActivityReader;
import dev.inspecto.core.property.RoomUsage;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Inspection data the property module needs for its card and for deleting rooms. */
@Component
public class InspectionStatistics implements PropertyActivityReader, RoomUsage {

  private final InspectionRepository inspections;
  private final ChecklistItemRepository items;

  /** Creates the component. */
  public InspectionStatistics(InspectionRepository inspections, ChecklistItemRepository items) {
    this.inspections = inspections;
    this.items = items;
  }

  /** Inspection counters; open defects are counted from T-203. */
  @Override
  public PropertyActivity read(UUID propertyId) {
    int count = Math.toIntExact(inspections.countByPropertyId(propertyId));
    return new PropertyActivity(count, 0, inspections.lastInspectedAt(propertyId));
  }

  @Override
  public boolean isUsed(UUID roomId) {
    return items.existsByRoomId(roomId);
  }
}
