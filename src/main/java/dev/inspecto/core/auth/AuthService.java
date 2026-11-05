package dev.inspecto.core.auth;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.common.id.UuidV7;
import dev.inspecto.core.security.AccessTokenService;
import dev.inspecto.core.security.AccessTokenService.IssuedAccessToken;
import dev.inspecto.core.security.CredentialService;
import dev.inspecto.core.security.RefreshTokenService;
import dev.inspecto.core.security.RefreshTokenService.Rotation;
import dev.inspecto.core.user.User;
import dev.inspecto.core.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration, sign-in, token refresh and sign-out. */
@Service
public class AuthService {

  private static final String EMAIL_TAKEN_MESSAGE = "Email is already registered";

  private final UserRepository users;
  private final CredentialService credentials;
  private final AccessTokenService accessTokens;
  private final RefreshTokenService refreshTokens;
  private final Clock clock;

  /** Creates the service. */
  public AuthService(
      UserRepository users,
      CredentialService credentials,
      AccessTokenService accessTokens,
      RefreshTokenService refreshTokens,
      Clock clock) {
    this.users = users;
    this.credentials = credentials;
    this.accessTokens = accessTokens;
    this.refreshTokens = refreshTokens;
    this.clock = clock;
  }

  /** Registers an inspector account. */
  @Transactional
  public User register(String email, String password, String fullName) {
    String normalized = normalize(email);
    String hash = credentials.hashNewPassword(password, normalized, "password");
    if (users.existsByEmail(normalized)) {
      throw new ApiException(ErrorCode.EMAIL_TAKEN, EMAIL_TAKEN_MESSAGE);
    }
    User user =
        new User(UuidV7.generate(clock), normalized, hash, fullName.trim(), clock.instant());
    try {
      return users.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(ErrorCode.EMAIL_TAKEN, EMAIL_TAKEN_MESSAGE, e);
    }
  }

  /** Verifies credentials and issues a token pair. */
  @Transactional(noRollbackFor = ApiException.class)
  public Tokens login(String email, String password, String userAgent) {
    Instant now = clock.instant();
    User user =
        users
            .findByEmail(normalize(email))
            .orElseThrow(() -> credentials.rejectUnknownUser(password));
    credentials.verify(user, password, now);
    String refresh = refreshTokens.issue(user.getId(), userAgent);
    return new Tokens(accessTokens.issue(user), refresh);
  }

  /** Rotates the refresh token and issues a new access token. */
  @Transactional(noRollbackFor = ApiException.class)
  public Tokens refresh(String refreshToken, String userAgent) {
    Rotation rotation = refreshTokens.rotate(refreshToken, userAgent);
    User user =
        users
            .findById(rotation.userId())
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Unknown user"));
    return new Tokens(accessTokens.issue(user), rotation.refreshToken());
  }

  /** Revokes the given refresh token. */
  public void logout(String refreshToken) {
    refreshTokens.revoke(refreshToken);
  }

  private static String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  /** Access and refresh token pair. */
  public record Tokens(IssuedAccessToken access, String refreshToken) {}
}
