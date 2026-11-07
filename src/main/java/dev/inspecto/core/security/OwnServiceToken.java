package dev.inspecto.core.security;

import dev.inspecto.core.security.ServiceTokenService.IssuedServiceToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A service token that core-service signs for its own calls to another service, reused until
 * shortly before it expires. One instance per client and scope; safe for concurrent use.
 */
public final class OwnServiceToken {

  /** A token is renewed this long before it expires. */
  private static final Duration RENEW_MARGIN = Duration.ofSeconds(30);

  private final ServiceTokenService tokens;
  private final Clock clock;
  private final List<String> scopes;

  private final ReentrantLock lock = new ReentrantLock();
  private String token;
  private Instant renewAt = Instant.MIN;

  /** Creates the token holder for the given scope. */
  public OwnServiceToken(ServiceTokenService tokens, Clock clock, String scope) {
    this.tokens = tokens;
    this.clock = clock;
    this.scopes = List.of(scope);
  }

  /** Value of the {@code Authorization} header. */
  public String bearer() {
    lock.lock();
    try {
      Instant now = clock.instant();
      if (token == null || !now.isBefore(renewAt)) {
        IssuedServiceToken issued = tokens.issueOwn(scopes);
        token = issued.value();
        renewAt = now.plusSeconds(issued.expiresInSeconds()).minus(RENEW_MARGIN);
      }
      return "Bearer " + token;
    } finally {
      lock.unlock();
    }
  }
}
