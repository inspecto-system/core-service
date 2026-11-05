package dev.inspecto.core.security;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Service-to-service authentication settings (prefix {@code inspecto.service-clients}).
 *
 * @param audience value of the {@code aud} claim of service tokens
 * @param tokenTtl service token lifetime
 * @param secrets secret of every enabled client by {@code client_id}; a client without a secret
 *     cannot obtain tokens
 */
@ConfigurationProperties("inspecto.service-clients")
public record ServiceClientProperties(
    String audience, Duration tokenTtl, Map<String, String> secrets) {

  /** Copies the secret map. */
  public ServiceClientProperties {
    secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
  }
}
