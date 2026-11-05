package dev.inspecto.core.inspection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Entry of the append-only status history of a defect (docs/DATABASE.md §3.5). */
@Entity
@Table(name = "defect_status_history")
public class DefectStatusChange {

  @Id private UUID id;

  @Column(name = "defect_id", nullable = false, updatable = false)
  private UUID defectId;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", updatable = false)
  private DefectStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false, updatable = false)
  private DefectStatus toStatus;

  @Column(name = "inspection_id", updatable = false)
  private UUID inspectionId;

  @Column(name = "changed_by", nullable = false, updatable = false)
  private UUID changedBy;

  @Column(name = "changed_at", nullable = false, updatable = false)
  private Instant changedAt;

  @Column(updatable = false)
  private String comment;

  protected DefectStatusChange() {
    // for JPA
  }

  /** Records a change of the current status. */
  public DefectStatusChange(UUID id, UUID defectId, Transition transition, Cause cause) {
    this.id = id;
    this.defectId = defectId;
    this.fromStatus = transition.from();
    this.toStatus = transition.to();
    this.inspectionId = cause.inspectionId();
    this.changedBy = cause.userId();
    this.changedAt = cause.at();
    this.comment = cause.comment();
  }

  public UUID getId() {
    return id;
  }

  public DefectStatus getFromStatus() {
    return fromStatus;
  }

  public DefectStatus getToStatus() {
    return toStatus;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public UUID getChangedBy() {
    return changedBy;
  }

  public Instant getChangedAt() {
    return changedAt;
  }

  public String getComment() {
    return comment;
  }

  /**
   * Status before and after the change.
   *
   * @param from {@code null} when the defect was created
   */
  public record Transition(DefectStatus from, DefectStatus to) {}

  /**
   * What caused the change.
   *
   * @param inspectionId inspection of the observation that changed the status
   * @param comment comment of that observation, if any
   */
  public record Cause(UUID inspectionId, UUID userId, Instant at, String comment) {}
}
