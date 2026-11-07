package dev.inspecto.core.report;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection to report-service (prefix {@code inspecto.report}).
 *
 * @param url base URL of report-service in the internal network
 * @param timeout connect and read timeout of one call
 */
@ConfigurationProperties("inspecto.report")
public record ReportProperties(String url, Duration timeout) {}
