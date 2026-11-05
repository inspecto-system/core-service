package dev.inspecto.core.property;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.common.id.UuidV7;
import dev.inspecto.core.outbox.OutboxEventType;
import dev.inspecto.core.outbox.OutboxService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Properties of the signed-in user (T-103, ADR-018). */
@Service
public class PropertyService {

  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

  private final PropertyRepository properties;
  private final RoomRepository rooms;
  private final PropertyAccess access;
  private final OutboxService outbox;
  private final Clock clock;

  /** Creates the service. */
  public PropertyService(
      PropertyRepository properties,
      RoomRepository rooms,
      PropertyAccess access,
      OutboxService outbox,
      Clock clock) {
    this.properties = properties;
    this.rooms = rooms;
    this.access = access;
    this.outbox = outbox;
    this.clock = clock;
  }

  /** Own properties, newest first, optionally filtered by a substring of the name or address. */
  @Transactional(readOnly = true)
  public Page<Property> list(UUID userId, boolean archived, String query, int page, int size) {
    return properties.search(
        userId, archived, likePattern(query), PageRequest.of(page, size, NEWEST_FIRST));
  }

  /** Creates a property owned by the user. */
  @Transactional
  public Property create(UUID userId, PropertyFields fields) {
    Property property = new Property(UuidV7.generate(clock), userId, fields, clock.instant());
    return properties.save(property);
  }

  /** Property card data. */
  @Transactional(readOnly = true)
  public PropertyCard get(UUID propertyId, UUID userId) {
    Property property = access.ownedProperty(propertyId, userId);
    return new PropertyCard(property, rooms.findByPropertyIdOrderBySortOrderAscIdAsc(propertyId));
  }

  /**
   * Updates the descriptive fields.
   *
   * @throws ApiException {@code VERSION_CONFLICT} when the version is not the current one
   */
  @Transactional
  public Property update(UUID propertyId, UUID userId, PropertyUpdate update) {
    Property property = access.ownedProperty(propertyId, userId);
    requireVersion(property.getVersion(), update.expectedVersion());
    property.apply(update.fields().apply(property.fields()), clock.instant());
    return properties.saveAndFlush(property);
  }

  /** Archives the property (reversible, ADR-018). */
  @Transactional
  public Property archive(UUID propertyId, UUID userId) {
    Property property = access.ownedProperty(propertyId, userId);
    property.archive(clock.instant());
    return properties.saveAndFlush(property);
  }

  /** Restores an archived property. */
  @Transactional
  public Property unarchive(UUID propertyId, UUID userId) {
    Property property = access.ownedProperty(propertyId, userId);
    property.unarchive(clock.instant());
    return properties.saveAndFlush(property);
  }

  /**
   * Deletes the property with all its data and schedules removal of its files in media-service and
   * report-service (ADR-018).
   */
  @Transactional
  public void delete(UUID propertyId, UUID userId) {
    Property property = access.ownedProperty(propertyId, userId);
    properties.delete(property);
    outbox.enqueue(
        OutboxEventType.DELETE_PROPERTY_FILES, Map.of("propertyId", propertyId.toString()));
  }

  static void requireVersion(long current, long expected) {
    if (current != expected) {
      throw new ApiException(ErrorCode.VERSION_CONFLICT, "The resource was changed meanwhile");
    }
  }

  /** {@code LIKE} pattern matching any value that contains the query, case-insensitively. */
  static String likePattern(String query) {
    if (query == null || query.isBlank()) {
      return "%";
    }
    String escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }

  /** Property with its rooms ordered for display. */
  public record PropertyCard(Property property, List<Room> rooms) {

    /** Copies the room list. */
    public PropertyCard {
      rooms = List.copyOf(rooms);
    }
  }

  /** Changes to apply and the version the client based them on. */
  public record PropertyUpdate(FieldsChange fields, long expectedVersion) {}

  /** Computes new descriptive fields from the current ones. */
  @FunctionalInterface
  public interface FieldsChange {
    PropertyFields apply(PropertyFields current);
  }
}
