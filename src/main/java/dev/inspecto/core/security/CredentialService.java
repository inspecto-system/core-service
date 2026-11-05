package dev.inspecto.core.security;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.user.User;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Password hashing, strength rules and verification with account lockout (SECURITY.md §2). */
@Service
public class CredentialService {

  private final PasswordEncoder encoder;
  private final PasswordPolicy policy;
  private final SecurityProperties properties;
  private final String dummyHash;

  /** Creates the service. */
  public CredentialService(
      PasswordEncoder encoder, PasswordPolicy policy, SecurityProperties properties) {
    this.encoder = encoder;
    this.policy = policy;
    this.properties = properties;
    this.dummyHash = encoder.encode("inspecto-timing-equalizer");
  }

  /** Checks strength and returns the hash of a new password. */
  public String hashNewPassword(String password, String email, String field) {
    policy.check(password, email, field);
    return encoder.encode(password);
  }

  /**
   * Verifies the password of an existing account, counting failures and locking the account.
   *
   * @throws ApiException {@code ACCOUNT_LOCKED} or {@code INVALID_CREDENTIALS}
   */
  public void verify(User user, String password, Instant now) {
    if (user.isLocked(now)) {
      throw new ApiException(ErrorCode.ACCOUNT_LOCKED, "Account is temporarily locked");
    }
    if (!encoder.matches(password, user.getPasswordHash())) {
      user.registerFailedLogin(now, properties.maxFailedLogins(), properties.lockDuration());
      throw invalidCredentials();
    }
    user.registerSuccessfulLogin(now);
  }

  /** Spends the same time as a real check and fails; used when the email is unknown. */
  public ApiException rejectUnknownUser(String password) {
    encoder.matches(password, dummyHash);
    return invalidCredentials();
  }

  /** Whether the password matches the account, without lockout bookkeeping. */
  public boolean matches(User user, String password) {
    return encoder.matches(password, user.getPasswordHash());
  }

  private static ApiException invalidCredentials() {
    return new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
  }
}
