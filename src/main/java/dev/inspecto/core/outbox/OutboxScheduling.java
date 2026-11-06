package dev.inspecto.core.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs the outbox processor in the background unless {@code inspecto.outbox.enabled} is false. */
@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty("inspecto.outbox.enabled")
public class OutboxScheduling {

  private final OutboxProcessor processor;

  /** Creates the scheduler. */
  public OutboxScheduling(OutboxProcessor processor) {
    this.processor = processor;
  }

  /** One delivery round; the next starts after the configured pause. */
  @Scheduled(fixedDelayString = "${inspecto.outbox.poll-interval}")
  void deliver() {
    processor.processDue();
  }
}
