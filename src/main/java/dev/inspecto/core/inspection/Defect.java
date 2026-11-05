package dev.inspecto.core.inspection;

import dev.inspecto.core.dictionary.ElementGroup;
import dev.inspecto.core.dictionary.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Defect of a property; it outlives a single inspection and is observed in every inspection that
 * checks it (ADR-003, docs/DATABASE.md §3.5).
 */
@Entity
@Table(name = "defects")
public class Defect {

  @Id private UUID id;

  @Column(name = "property_id", nullable = false, updatable = false)
  private UUID propertyId;

  @Column(name = "room_id", nullable = false, updatable = false)
  private UUID roomId;

  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Enumerated(EnumType.STRING)
  @Column(name = "element_group", nullable = false, updatable = false)
  private ElementGroup elementGroup;

  @Column(nullable = false)
  private String title;

  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Severity severity;

  @Enumerated(EnumType.STRING)
  @Column(name = "current_status", nullable = false)
  private DefectStatus currentStatus;

  @Column(name = "first_inspection_id", nullable = false, updatable = false)
  private UUID firstInspectionId;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected Defect() {
    // for JPA
  }

  /** Creates an open defect found in the given inspection. */
  public Defect(UUID id, DefectOrigin origin, DefectFields fields, Instant now) {
    this.id = id;
    this.propertyId = origin.propertyId();
    this.roomId = origin.roomId();
    this.elementGroup = origin.elementGroup();
    this.firstInspectionId = origin.firstInspectionId();
    this.createdBy = origin.createdBy();
    this.currentStatus = DefectStatus.OPEN;
    this.createdAt = now;
    apply(fields, now);
  }

  /** Replaces the editable fields. */
  public final void apply(DefectFields fields, Instant now) {
    this.categoryId = fields.categoryId();
    this.title = fields.title();
    this.description = fields.description();
    this.severity = fields.severity();
    this.updatedAt = now;
  }

  /** Sets the status taken from the latest observation. */
  public void changeStatus(DefectStatus status, Instant now) {
    this.currentStatus = status;
    this.updatedAt = now;
  }

  /** Current editable fields. */
  public DefectFields fields() {
    return new DefectFields(categoryId, title, description, severity);
  }

  public UUID getId() {
    return id;
  }

  public UUID getPropertyId() {
    return propertyId;
  }

  public UUID getRoomId() {
    return roomId;
  }

  public ElementGroup getElementGroup() {
    return elementGroup;
  }

  public DefectStatus getCurrentStatus() {
    return currentStatus;
  }

  public UUID getFirstInspectionId() {
    return firstInspectionId;
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

  /** Where and by whom a defect was found; these fields never change. */
  public record DefectOrigin(
      UUID propertyId,
      UUID roomId,
      ElementGroup elementGroup,
      UUID firstInspectionId,
      UUID createdBy) {}

  /** Editable fields of a defect; {@code description} is optional. */
  public record DefectFields(
      UUID categoryId, String title, String description, Severity severity) {}
}
