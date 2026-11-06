package dev.inspecto.core.media;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection to media-service (prefix {@code inspecto.media}).
 *
 * @param url base URL of media-service in the internal network
 * @param timeout connect and read timeout of one call
 * @param maxPhotosPerObservation photos allowed on one observation; media-service publishes the
 *     same value in {@code GET /photos/limits}
 */
@ConfigurationProperties("inspecto.media")
public record MediaProperties(String url, Duration timeout, int maxPhotosPerObservation) {}
