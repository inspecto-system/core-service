package dev.inspecto.core.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Delivery of outbox jobs (prefix {@code inspecto.outbox}).
 *
 * @param enabled whether the background delivery runs; tests switch it off and drive the processor
 * @param pollInterval pause between two delivery rounds
 * @param batchSize jobs taken in one round
 * @param maxRetryDelay upper bound of the growing delay between retries of a failed job
 */
@ConfigurationProperties("inspecto.outbox")
public record OutboxProperties(
    boolean enabled, Duration pollInterval, int batchSize, Duration maxRetryDelay) {}
