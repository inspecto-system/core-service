package dev.inspecto.core.outbox;

import dev.inspecto.core.media.MediaUnavailableException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivers due outbox jobs. A failed job is retried with a growing delay, capped by the
 * configuration, until it succeeds (ADR-018); it is never dropped.
 */
@Component
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(OutboxProcessor.class);

  /** Delay before the first retry; it doubles with every failed attempt. */
  static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(10);

  private final OutboxEventRepository events;
  private final OutboxHandler handler;
  private final OutboxProperties properties;
  private final Clock clock;

  /** Creates the processor. */
  public OutboxProcessor(
      OutboxEventRepository events,
      OutboxHandler handler,
      OutboxProperties properties,
      Clock clock) {
    this.events = events;
    this.handler = handler;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Runs one batch of due jobs in one transaction, holding their row locks, so another instance
   * skips them.
   *
   * @return number of jobs completed
   */
  @Transactional
  public int processDue() {
    Instant now = clock.instant();
    List<OutboxEvent> due = events.lockDue(now, Limit.of(properties.batchSize()));
    int done = 0;
    for (OutboxEvent event : due) {
      if (run(event, now)) {
        done++;
      }
    }
    return done;
  }

  @SuppressFBWarnings(
      value = "CRLF_INJECTION_LOGS",
      justification =
          "Logs are JSON (ECS), which escapes line breaks; the values are an id, an enum and a time")
  private boolean run(OutboxEvent event, Instant now) {
    try {
      handler.handle(event);
      event.markDone();
      return true;
    } catch (MediaUnavailableException | IllegalArgumentException e) {
      Instant next = now.plus(retryDelay(event.getAttempts()));
      // Only the exception type and its message are kept: they never contain tokens or user data.
      event.retryAt(next, e.getClass().getSimpleName() + ": " + e.getMessage());
      LOG.warn(
          "Outbox job {} of type {} failed, retry at {}", event.getId(), event.getType(), next, e);
      return false;
    }
  }

  /** Delay after the given number of earlier failed attempts. */
  Duration retryDelay(int attempts) {
    Duration delay = FIRST_RETRY_DELAY;
    for (int i = 0; i < attempts && delay.compareTo(properties.maxRetryDelay()) < 0; i++) {
      delay = delay.multipliedBy(2);
    }
    return delay.compareTo(properties.maxRetryDelay()) > 0 ? properties.maxRetryDelay() : delay;
  }
}
