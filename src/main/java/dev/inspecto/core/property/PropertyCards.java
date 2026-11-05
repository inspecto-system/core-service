package dev.inspecto.core.property;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Property card: the property, its rooms and inspection counters. */
@Service
public class PropertyCards {

  private final PropertyAccess access;
  private final RoomRepository rooms;
  private final PropertyActivityReader activity;

  /** Creates the service. */
  public PropertyCards(
      PropertyAccess access, RoomRepository rooms, PropertyActivityReader activity) {
    this.access = access;
    this.rooms = rooms;
    this.activity = activity;
  }

  /** Card of an own property. */
  @Transactional(readOnly = true)
  public PropertyCard get(UUID propertyId, UUID userId) {
    Property property = access.ownedProperty(propertyId, userId);
    return new PropertyCard(
        property,
        rooms.findByPropertyIdOrderBySortOrderAscIdAsc(propertyId),
        activity.read(propertyId));
  }

  /** Property with its rooms ordered for display and its inspection activity. */
  public record PropertyCard(Property property, List<Room> rooms, PropertyActivity activity) {

    /** Copies the room list. */
    public PropertyCard {
      rooms = List.copyOf(rooms);
    }
  }
}
