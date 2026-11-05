package dev.inspecto.core.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Authentication settings (prefix {@code inspecto.security}).
 *
 * @param issuer value of the {@code iss} claim
 * @param audience value of the {@code aud} claim of user access tokens
 * @param accessTokenTtl access token lifetime
 * @param refreshTokenTtl refresh token lifetime
 * @param keyId identifier ({@code kid}) of the signing key
 * @param privateKey PKCS#8 PEM RSA private key; when empty an ephemeral key is generated
 * @param maxFailedLogins failed sign-ins before the account is locked
 * @param lockDuration how long the account stays locked
 * @param cookieSecure whether the refresh cookie is marked {@code Secure}
 */
@ConfigurationProperties("inspecto.security")
public record SecurityProperties(
    String issuer,
    String audience,
    Duration accessTokenTtl,
    Duration refreshTokenTtl,
    String keyId,
    String privateKey,
    int maxFailedLogins,
    Duration lockDuration,
    boolean cookieSecure) {}
