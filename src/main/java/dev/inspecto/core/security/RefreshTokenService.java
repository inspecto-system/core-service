package dev.inspecto.core.security;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.common.id.UuidV7;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh tokens with rotation and reuse detection (ADR-007): every use replaces the token,
 * and presenting an already used token revokes the whole family.
 */
@Service
public class RefreshTokenService {

  private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenService.class);
  private static final int TOKEN_BYTES = 32;

  private final RefreshTokenRepository repository;
  private final SecurityProperties properties;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  /** Creates the service. */
  public RefreshTokenService(
      RefreshTokenRepository repository, SecurityProperties properties, Clock clock) {
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
  }

  /** Starts a new token family for the user and returns the raw token. */
  @Transactional
  public String issue(UUID userId, String userAgent) {
    return create(userId, UuidV7.generate(clock), userAgent);
  }

  /**
   * Validates and replaces a token.
   *
   * @return the owner and the new raw token
   * @throws ApiException {@code UNAUTHENTICATED} when the token is unknown, expired or reused
   */
  @Transactional(noRollbackFor = ApiException.class)
  public Rotation rotate(String rawToken, String userAgent) {
    Instant now = clock.instant();
    RefreshToken current = repository.findByTokenHash(hash(rawToken)).orElseThrow(this::invalid);
    if (current.isRevoked()) {
      LOG.warn("Refresh token reuse detected, revoking family {}", current.getFamilyId());
      repository.revokeFamily(current.getFamilyId(), now);
      throw invalid();
    }
    if (current.isExpired(now)) {
      throw invalid();
    }
    current.revoke(now);
    String next = create(current.getUserId(), current.getFamilyId(), userAgent);
    return new Rotation(current.getUserId(), next);
  }

  /** Revokes a token if it exists (idempotent). */
  @Transactional
  public void revoke(String rawToken) {
    repository.findByTokenHash(hash(rawToken)).ifPresent(t -> t.revoke(clock.instant()));
  }

  /** Revokes every active token of the user, for example after a password change. */
  @Transactional
  public void revokeAll(UUID userId) {
    repository.revokeAllOfUser(userId, clock.instant());
  }

  private String create(UUID userId, UUID familyId, String userAgent) {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant now = clock.instant();
    Instant expiresAt = now.plus(properties.refreshTokenTtl());
    RefreshToken token =
        new RefreshToken(UuidV7.generate(clock), userId, familyId, hash(raw), expiresAt);
    token.setCreation(now, userAgent);
    repository.save(token);
    return raw;
  }

  private ApiException invalid() {
    return new ApiException(ErrorCode.UNAUTHENTICATED, "Refresh token is invalid");
  }

  static String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] value = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(value);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  /** Result of a rotation. */
  public record Rotation(UUID userId, String refreshToken) {}
}
