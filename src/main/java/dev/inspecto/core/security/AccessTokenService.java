package dev.inspecto.core.security;

import dev.inspecto.core.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived user access tokens (JWT RS256, ADR-007). */
@Service
public class AccessTokenService {

  private final JwtEncoder encoder;
  private final SecurityProperties properties;
  private final JwtKeyProvider keys;
  private final Clock clock;

  /** Creates the service. */
  public AccessTokenService(
      JwtEncoder encoder, SecurityProperties properties, JwtKeyProvider keys, Clock clock) {
    this.encoder = encoder;
    this.properties = properties;
    this.keys = keys;
    this.clock = clock;
  }

  /** Issues an access token for the user. */
  public IssuedAccessToken issue(User user) {
    Instant now = clock.instant();
    Instant expiresAt = now.plus(properties.accessTokenTtl());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .audience(List.of(properties.audience()))
            .subject(user.getId().toString())
            .claim("role", user.getRole().name())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .build();
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(keys.signingKey().getKeyID()).build();
    String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new IssuedAccessToken(token, properties.accessTokenTtl().toSeconds());
  }

  /** Signed token and its lifetime in seconds. */
  public record IssuedAccessToken(String value, long expiresInSeconds) {}
}
