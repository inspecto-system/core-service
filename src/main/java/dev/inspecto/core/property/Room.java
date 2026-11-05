package dev.inspecto.core.property;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Room of a property; rooms are inspected one by one (docs/DATABASE.md §3.2). */
@Entity
@Table(name = "rooms")
public class Room {

  @Id private UUID id;

  @Column(name = "property_id", nullable = false, updatable = false)
  private UUID propertyId;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "room_type", nullable = false)
  private RoomType roomType;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected Room() {
    // for JPA
  }

  /** Creates a room of the given property; all fields must be given. */
  public Room(UUID id, UUID propertyId, RoomFields fields, Instant now) {
    this.id = id;
    this.propertyId = propertyId;
    this.name = fields.name();
    this.roomType = fields.roomType();
    this.sortOrder = fields.sortOrder();
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** Updates the given fields; {@code null} keeps the current value. */
  public void update(RoomFields change, Instant now) {
    if (change.name() != null) {
      name = change.name();
    }
    if (change.roomType() != null) {
      roomType = change.roomType();
    }
    if (change.sortOrder() != null) {
      sortOrder = change.sortOrder();
    }
    updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPropertyId() {
    return propertyId;
  }

  public String getName() {
    return name;
  }

  public RoomType getRoomType() {
    return roomType;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public long getVersion() {
    return version;
  }
}
