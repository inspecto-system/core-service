package dev.inspecto.core.inspection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code Idempotency-Key} of a created defect (V8): the same key of the same user means the same
 * defect.
 */
@Entity
@Table(name = "defect_idempotency_keys")
public class DefectIdempotencyKey {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(nullable = false, updatable = false)
  private String key;

  @Column(name = "defect_id", nullable = false, updatable = false)
  private UUID defectId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected DefectIdempotencyKey() {
    // for JPA
  }

  /** Remembers that the user's key created the defect. */
  public DefectIdempotencyKey(UUID id, UUID userId, String key, UUID defectId, Instant now) {
    this.id = id;
    this.userId = userId;
    this.key = key;
    this.defectId = defectId;
    this.createdAt = now;
  }

  public UUID getDefectId() {
    return defectId;
  }
}
