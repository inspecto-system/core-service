package dev.inspecto.core.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Stored refresh token: only its SHA-256 hash is kept (SECURITY.md §2). */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "family_id", nullable = false)
  private UUID familyId;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "user_agent")
  private String userAgent;

  protected RefreshToken() {
    // for JPA
  }

  RefreshToken(UUID id, UUID userId, UUID familyId, String tokenHash, Instant expiresAt) {
    this.id = id;
    this.userId = userId;
    this.familyId = familyId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  void setCreation(Instant now, String agent) {
    this.createdAt = now;
    this.userAgent = agent;
  }

  boolean isRevoked() {
    return revokedAt != null;
  }

  boolean isExpired(Instant now) {
    return !expiresAt.isAfter(now);
  }

  void revoke(Instant now) {
    if (revokedAt == null) {
      revokedAt = now;
    }
  }

  UUID getUserId() {
    return userId;
  }

  UUID getFamilyId() {
    return familyId;
  }
}
