package dev.inspecto.core.common.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

  @Test
  void shouldGenerateVersion7WithRfcVariant() {
    UUID id = UuidV7.generate(Clock.systemUTC());

    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2);
  }

  @Test
  void shouldEncodeTimestampInMostSignificantBits() {
    Instant instant = Instant.parse("2026-11-04T12:00:00Z");
    UUID id = UuidV7.generate(Clock.fixed(instant, ZoneOffset.UTC));

    assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(instant.toEpochMilli());
  }

  @Test
  void shouldOrderIdentifiersByTime() {
    UUID earlier = UuidV7.generate(Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC));
    UUID later = UuidV7.generate(Clock.fixed(Instant.ofEpochMilli(2_000), ZoneOffset.UTC));

    assertThat(earlier.toString()).isLessThan(later.toString());
  }
}
