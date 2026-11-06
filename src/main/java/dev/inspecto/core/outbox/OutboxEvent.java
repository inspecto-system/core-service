package dev.inspecto.core.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/**
 * Deferred clean-up job written in the same transaction as the deletion that caused it (ADR-018,
 * docs/DATABASE.md §3.6). A background processor delivers it to the owning services.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private OutboxEventType type;

  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  @ColumnTransformer(write = "?::jsonb")
  private String payload;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected OutboxEvent() {
    // for JPA
  }

  /** Creates a pending event that is due immediately. */
  public OutboxEvent(UUID id, OutboxEventType type, String payload, Instant now) {
    this.id = id;
    this.type = type;
    this.payload = payload;
    this.status = "PENDING";
    this.createdAt = now;
    this.nextAttemptAt = now;
  }

  /** Marks the job as done; it is not taken again. */
  public void markDone() {
    status = "DONE";
    lastError = null;
  }

  /**
   * Records a failed attempt; the job is taken again at the given time. Jobs are retried until they
   * succeed (ADR-018).
   */
  public void retryAt(Instant next, String error) {
    attempts++;
    nextAttemptAt = next;
    lastError = error;
  }

  public UUID getId() {
    return id;
  }

  public OutboxEventType getType() {
    return type;
  }

  public String getPayload() {
    return payload;
  }

  public String getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public String getLastError() {
    return lastError;
  }
}
