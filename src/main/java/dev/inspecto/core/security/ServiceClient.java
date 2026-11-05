package dev.inspecto.core.security;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Services that may obtain a service token (client credentials, ADR-007) and the scopes of their
 * tokens. A scope grants access to one group of {@code /internal/**} endpoints of core-service.
 */
public enum ServiceClient {
  MEDIA("media-service", List.of(ServiceClient.ACCESS_CHECK)),
  REPORT("report-service", List.of(ServiceClient.ACCESS_CHECK)),
  // ml-service calls only media-service, which checks the token itself.
  ML("ml-service", List.of());

  /** Scope of {@code GET /internal/v1/access/properties/{id}}. */
  public static final String ACCESS_CHECK = "access.check";

  private final String clientId;
  private final List<String> scopes;

  ServiceClient(String clientId, List<String> scopes) {
    this.clientId = clientId;
    this.scopes = scopes;
  }

  /** Client with the given {@code client_id}. */
  public static Optional<ServiceClient> byClientId(String clientId) {
    return Arrays.stream(values()).filter(c -> c.clientId.equals(clientId)).findFirst();
  }

  public String clientId() {
    return clientId;
  }

  public List<String> scopes() {
    return List.copyOf(scopes);
  }
}
