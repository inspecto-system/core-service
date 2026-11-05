package dev.inspecto.core.property;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.common.error.OptimisticVersion;
import dev.inspecto.core.common.id.UuidV7;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rooms of the user's properties (T-103). */
@Service
public class RoomService {

  private final RoomRepository rooms;
  private final PropertyAccess access;
  private final RoomUsage usage;
  private final Clock clock;

  /** Creates the service. */
  public RoomService(RoomRepository rooms, PropertyAccess access, RoomUsage usage, Clock clock) {
    this.rooms = rooms;
    this.access = access;
    this.usage = usage;
    this.clock = clock;
  }

  /** Rooms of an own property ordered by {@code sortOrder}. */
  @Transactional(readOnly = true)
  public List<Room> list(UUID propertyId, UUID userId) {
    access.ownedProperty(propertyId, userId);
    return rooms.findByPropertyIdOrderBySortOrderAscIdAsc(propertyId);
  }

  /** Adds a room; without a sort order it goes after the existing rooms. */
  @Transactional
  public Room create(UUID propertyId, UUID userId, RoomFields fields) {
    access.ownedProperty(propertyId, userId);
    int sortOrder =
        fields.sortOrder() == null ? rooms.maxSortOrder(propertyId) + 1 : fields.sortOrder();
    RoomFields complete = new RoomFields(fields.name(), fields.roomType(), sortOrder);
    return rooms.save(new Room(UuidV7.generate(clock), propertyId, complete, clock.instant()));
  }

  /** Updates a room with optimistic locking. */
  @Transactional
  public Room update(UUID roomId, UUID userId, RoomFields change, long expectedVersion) {
    Room room = access.ownedRoom(roomId, userId);
    OptimisticVersion.require(room.getVersion(), expectedVersion);
    room.update(change, clock.instant());
    return rooms.saveAndFlush(room);
  }

  /**
   * Deletes a room that no inspection refers to.
   *
   * @throws ApiException {@code RESOURCE_IN_USE} when the room is part of an inspection
   */
  @Transactional
  public void delete(UUID roomId, UUID userId) {
    Room room = access.ownedRoom(roomId, userId);
    if (usage.isUsed(roomId)) {
      throw new ApiException(ErrorCode.RESOURCE_IN_USE, "The room is used by an inspection");
    }
    rooms.delete(room);
  }
}
