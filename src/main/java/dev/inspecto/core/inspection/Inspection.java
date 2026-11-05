package dev.inspecto.core.inspection;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
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
 * Inspection of a property (docs/DATABASE.md §3.4). The status only moves forward: a draft is
 * started, a started inspection is completed, and a completed one cannot be changed.
 */
@Entity
@Table(name = "inspections")
public class Inspection {

  @Id private UUID id;

  @Column(name = "property_id", nullable = false, updatable = false)
  private UUID propertyId;

  @Column(name = "inspector_id", nullable = false, updatable = false)
  private UUID inspectorId;

  private String title;

  @Column(name = "inspected_at", nullable = false)
  private Instant inspectedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InspectionStatus status;

  private String notes;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected Inspection() {
    // for JPA
  }

  /** Creates a draft inspection of a property carried out by the given user. */
  public Inspection(
      UUID id, UUID propertyId, UUID inspectorId, InspectionFields fields, Instant now) {
    this.id = id;
    this.propertyId = propertyId;
    this.inspectorId = inspectorId;
    this.status = InspectionStatus.DRAFT;
    this.createdAt = now;
    apply(fields, now);
  }

  /**
   * Replaces the descriptive fields and, when asked, starts a draft. Starting an inspection that is
   * already in progress changes nothing.
   *
   * @throws ApiException {@code INVALID_STATE_TRANSITION} when the inspection is completed
   */
  public void update(InspectionFields fields, boolean start, Instant now) {
    requireNotCompleted();
    apply(fields, now);
    if (start) {
      status = InspectionStatus.IN_PROGRESS;
    }
  }

  /**
   * Completes a started inspection.
   *
   * @throws ApiException {@code INVALID_STATE_TRANSITION} unless the inspection is in progress
   */
  public void complete(Instant now) {
    if (status != InspectionStatus.IN_PROGRESS) {
      throw transitionError("Only an inspection in progress can be completed");
    }
    status = InspectionStatus.COMPLETED;
    completedAt = now;
    updatedAt = now;
  }

  /**
   * Checks that the inspection may be deleted.
   *
   * @throws ApiException {@code INVALID_STATE_TRANSITION} unless the inspection is a draft
   */
  public void requireDeletable() {
    if (status != InspectionStatus.DRAFT) {
      throw transitionError("Only a draft inspection can be deleted");
    }
  }

  /**
   * Checks that the inspection and its checklist may still be changed.
   *
   * @throws ApiException {@code INVALID_STATE_TRANSITION} when the inspection is completed
   */
  public void requireNotCompleted() {
    if (status == InspectionStatus.COMPLETED) {
      throw transitionError("A completed inspection cannot be changed");
    }
  }

  /** Current descriptive fields. */
  public InspectionFields fields() {
    return new InspectionFields(title, inspectedAt, notes);
  }

  private void apply(InspectionFields fields, Instant now) {
    this.title = fields.title();
    this.inspectedAt = fields.inspectedAt();
    this.notes = fields.notes();
    this.updatedAt = now;
  }

  private static ApiException transitionError(String detail) {
    return new ApiException(ErrorCode.INVALID_STATE_TRANSITION, detail);
  }

  public UUID getId() {
    return id;
  }

  public UUID getPropertyId() {
    return propertyId;
  }

  public UUID getInspectorId() {
    return inspectorId;
  }

  public InspectionStatus getStatus() {
    return status;
  }

  public Instant getCompletedAt() {
    return completedAt;
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
