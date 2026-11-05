package dev.inspecto.core.property;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Real estate object owned by a user (docs/DATABASE.md §3.2). */
@Entity
@Table(name = "properties")
public class Property {

  @Id private UUID id;

  @Column(name = "owner_id", nullable = false, updatable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PropertyType type;

  @Column(nullable = false)
  private String address;

  private String city;

  @Column(name = "area_sqm")
  private BigDecimal areaSqm;

  private String notes;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected Property() {
    // for JPA
  }

  /** Creates a property owned by the given user. */
  public Property(UUID id, UUID ownerId, PropertyFields fields, Instant now) {
    this.id = id;
    this.ownerId = ownerId;
    this.createdAt = now;
    apply(fields, now);
  }

  /** Replaces the descriptive fields. */
  public final void apply(PropertyFields fields, Instant now) {
    this.name = fields.name();
    this.type = fields.type();
    this.address = fields.address();
    this.city = fields.city();
    this.areaSqm = fields.areaSqm();
    this.notes = fields.notes();
    this.updatedAt = now;
  }

  /** Current descriptive fields. */
  public PropertyFields fields() {
    return new PropertyFields(name, type, address, city, areaSqm, notes);
  }

  /** Hides the property from the default list; repeated calls keep the first timestamp. */
  public void archive(Instant now) {
    if (archivedAt == null) {
      archivedAt = now;
      updatedAt = now;
    }
  }

  /** Returns the property to the default list. */
  public void unarchive(Instant now) {
    if (archivedAt != null) {
      archivedAt = null;
      updatedAt = now;
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public long getVersion() {
    return version;
  }
}
