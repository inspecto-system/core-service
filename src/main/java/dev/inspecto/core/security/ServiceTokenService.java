package dev.inspecto.core.security;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues service tokens to media-service, report-service and ml-service (ADR-007). */
@Service
public final class ServiceTokenService {

  /** Shortest accepted client secret; secrets are generated, not chosen by people. */
  static final int MIN_SECRET_LENGTH = 32;

  private static final Logger LOG = LoggerFactory.getLogger(ServiceTokenService.class);

  private final JwtEncoder encoder;
  private final JwtKeyProvider keys;
  private final SecurityProperties security;
  private final ServiceClientProperties clients;
  private final Map<ServiceClient, byte[]> secretDigests;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @throws IllegalStateException when a secret is configured for an unknown client or is shorter
   *     than {@value #MIN_SECRET_LENGTH} characters
   */
  public ServiceTokenService(
      JwtEncoder encoder,
      JwtKeyProvider keys,
      SecurityProperties security,
      ServiceClientProperties clients,
      Clock clock) {
    this.encoder = encoder;
    this.keys = keys;
    this.security = security;
    this.clients = clients;
    this.secretDigests = digests(clients.secrets());
    this.clock = clock;
  }

  /**
   * Checks the client credentials and issues a token.
   *
   * @throws ApiException {@code UNAUTHENTICATED} when the client is unknown, has no secret or the
   *     secret does not match
   */
  @SuppressFBWarnings(
      value = "CRLF_INJECTION_LOGS",
      justification =
          "Only the name of a ServiceClient constant or '?' is logged, never the request value")
  public IssuedServiceToken issue(String clientId, String clientSecret) {
    Optional<ServiceClient> client = ServiceClient.byClientId(clientId);
    byte[] expected = client.map(secretDigests::get).orElse(null);
    // Compare even when the client is unknown so that the response time does not reveal it.
    boolean matches =
        MessageDigest.isEqual(sha256(clientSecret), expected == null ? new byte[32] : expected);
    if (!matches || expected == null) {
      LOG.warn("Rejected service token request for client {}", client.map(Enum::name).orElse("?"));
      throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid client credentials");
    }
    return sign(client.get());
  }

  private IssuedServiceToken sign(ServiceClient client) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(security.issuer())
            .audience(List.of(clients.audience()))
            .subject(client.clientId())
            .claim("scope", String.join(" ", client.scopes()))
            .issuedAt(now)
            .expiresAt(now.plus(clients.tokenTtl()))
            .id(UUID.randomUUID().toString())
            .build();
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(keys.signingKey().getKeyID()).build();
    String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new IssuedServiceToken(token, clients.tokenTtl().toSeconds());
  }

  private static Map<ServiceClient, byte[]> digests(Map<String, String> secrets) {
    Map<ServiceClient, byte[]> digests = new EnumMap<>(ServiceClient.class);
    secrets.forEach((clientId, secret) -> {
      ServiceClient client =
          ServiceClient.byClientId(clientId)
              .orElseThrow(() -> new IllegalStateException("Unknown service client: " + clientId));
      if (secret == null || secret.isBlank()) {
        return;
      }
      if (secret.length() < MIN_SECRET_LENGTH) {
        throw new IllegalStateException(
            "Secret of " + clientId + " is shorter than " + MIN_SECRET_LENGTH + " characters");
      }
      digests.put(client, sha256(secret));
    });
    return digests;
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  /** Signed token and its lifetime in seconds. */
  public record IssuedServiceToken(String value, long expiresInSeconds) {}
}
