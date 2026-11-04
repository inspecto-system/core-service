package dev.inspecto.core.common.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/** Generates time-ordered UUID version 7 identifiers (RFC 9562). */
public final class UuidV7 {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final long VERSION_BITS = 0x7000L;
  private static final long VARIANT_BITS = 0x8000_0000_0000_0000L;
  private static final long RANDOM_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL;

  private UuidV7() {}

  /** Returns a new identifier for the current time of the given clock. */
  public static UUID generate(Clock clock) {
    long timestamp = clock.millis() << 16;
    long randomA = RANDOM.nextInt(1 << 12);
    long randomB = RANDOM.nextLong() & RANDOM_B_MASK;
    return new UUID(timestamp | VERSION_BITS | randomA, randomB | VARIANT_BITS);
  }
}
