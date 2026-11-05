package dev.inspecto.core.inspection;

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
 * State of a defect in one inspection (ADR-003). A defect has at most one observation per
 * inspection; the observation of the latest inspection determines the current status.
 */
@Entity
@Table(name = "defect_observations")
public class DefectObservation {

  @Id private UUID id;

  @Column(name = "defect_id", nullable = false, updatable = false)
  private UUID defectId;

  @Column(name = "inspection_id", nullable = false, updatable = false)
  private UUID inspectionId;

  @Column(name = "checklist_item_id", updatable = false)
  private UUID checklistItemId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DefectStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Severity severity;

  private String comment;

  @Column(name = "observed_by", nullable = false, updatable = false)
  private UUID observedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected DefectObservation() {
    // for JPA
  }

  /** Records the state of a defect in an inspection. */
  public DefectObservation(UUID id, ObservationKey key, ObservationDraft draft, Instant now) {
    this.id = id;
    this.defectId = key.defectId();
    this.inspectionId = key.inspectionId();
    this.observedBy = key.observedBy();
    this.checklistItemId = draft.checklistItemId();
    this.createdAt = now;
    apply(draft.entry(), now);
  }

  /** Replaces status, severity and comment. */
  public final void apply(ObservationEntry entry, Instant now) {
    this.status = entry.status();
    this.severity = entry.severity();
    this.comment = entry.comment();
    this.updatedAt = now;
  }

  /** Current status, severity and comment. */
  public ObservationEntry entry() {
    return new ObservationEntry(status, severity, comment);
  }

  public UUID getId() {
    return id;
  }

  public UUID getDefectId() {
    return defectId;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public UUID getChecklistItemId() {
    return checklistItemId;
  }

  public DefectStatus getStatus() {
    return status;
  }

  public UUID getObservedBy() {
    return observedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public long getVersion() {
    return version;
  }

  /** Which defect was observed in which inspection and by whom. */
  public record ObservationKey(UUID defectId, UUID inspectionId, UUID observedBy) {}

  /** Fields of a new observation; {@code checklistItemId} is optional. */
  public record ObservationDraft(UUID checklistItemId, ObservationEntry entry) {}

  /** Status, severity and an optional comment. */
  public record ObservationEntry(DefectStatus status, Severity severity, String comment) {}
}
