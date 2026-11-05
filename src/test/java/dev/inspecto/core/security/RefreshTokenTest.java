package dev.inspecto.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

  private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

  @Test
  void shouldExpireAtExpirationInstant() {
    RefreshToken token = token(NOW);

    assertThat(token.isExpired(NOW.minusSeconds(1))).isFalse();
    assertThat(token.isExpired(NOW)).isTrue();
  }

  @Test
  void shouldKeepFirstRevocationTime() {
    RefreshToken token = token(NOW.plusSeconds(3_600));

    token.revoke(NOW);
    token.revoke(NOW.plusSeconds(60));

    assertThat(token.isRevoked()).isTrue();
  }

  @Test
  void shouldHashDeterministicallyWithoutKeepingTheToken() {
    String hash = RefreshTokenService.hash("raw-token");

    assertThat(hash).hasSize(64).isEqualTo(RefreshTokenService.hash("raw-token"));
    assertThat(hash).doesNotContain("raw-token");
  }

  private static RefreshToken token(Instant expiresAt) {
    UUID id = UUID.randomUUID();
    return new RefreshToken(id, UUID.randomUUID(), UUID.randomUUID(), "hash", expiresAt);
  }
}
