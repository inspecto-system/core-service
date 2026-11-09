package dev.inspecto.core.suggestion;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection to ml-service (prefix {@code inspecto.ml}).
 *
 * @param url base URL of ml-service in the internal network
 * @param timeout connect and read timeout of one classification (3 s, ml.yaml)
 */
@ConfigurationProperties("inspecto.ml")
public record MlProperties(String url, Duration timeout) {}
