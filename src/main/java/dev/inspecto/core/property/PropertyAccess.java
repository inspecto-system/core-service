package dev.inspecto.core.property;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The single place that decides whether a user may access a property and the resources under it
 * (T-102, ADR-009, SECURITY.md §3). Resources of other users are reported as missing ({@code 404})
 * so that their existence is not disclosed. In the MVP only the owner has access; access granted to
 * clients ({@code property_access}) will be checked here as well.
 */
@Component
public class PropertyAccess {

  private final PropertyRepository properties;
  private final RoomRepository rooms;

  /** Creates the component. */
  public PropertyAccess(PropertyRepository properties, RoomRepository rooms) {
    this.properties = properties;
    this.rooms = rooms;
  }

  /**
   * Returns the property if the user owns it.
   *
   * @throws ApiException {@code NOT_FOUND} when the property does not exist or is someone else's
   */
  public Property ownedProperty(UUID propertyId, UUID userId) {
    return properties
        .findByIdAndOwnerId(propertyId, userId)
        .orElseThrow(() -> notFound("Property not found"));
  }

  /**
   * Returns the room if the user owns its property.
   *
   * @throws ApiException {@code NOT_FOUND} when the room does not exist or is someone else's
   */
  public Room ownedRoom(UUID roomId, UUID userId) {
    return rooms.findOwned(roomId, userId).orElseThrow(() -> notFound("Room not found"));
  }

  private static ApiException notFound(String detail) {
    return new ApiException(ErrorCode.NOT_FOUND, detail);
  }
}
