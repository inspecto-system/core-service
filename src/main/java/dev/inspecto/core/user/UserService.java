package dev.inspecto.core.user;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.common.error.FieldViolation;
import dev.inspecto.core.security.CredentialService;
import dev.inspecto.core.security.RefreshTokenService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profile of the signed-in user. */
@Service
public class UserService {

  private final UserRepository users;
  private final CredentialService credentials;
  private final RefreshTokenService refreshTokens;
  private final Clock clock;

  /** Creates the service. */
  public UserService(
      UserRepository users,
      CredentialService credentials,
      RefreshTokenService refreshTokens,
      Clock clock) {
    this.users = users;
    this.credentials = credentials;
    this.refreshTokens = refreshTokens;
    this.clock = clock;
  }

  /** Returns the account or {@code NOT_FOUND} when it no longer exists. */
  @Transactional(readOnly = true)
  public User get(UUID userId) {
    return find(userId);
  }

  /** Updates the profile with optimistic locking. */
  @Transactional
  public User update(UUID userId, String fullName, String locale, long expectedVersion) {
    User user = find(userId);
    if (user.getVersion() != expectedVersion) {
      throw new ApiException(ErrorCode.VERSION_CONFLICT, "Profile was changed by another request");
    }
    String trimmedName = fullName == null ? null : fullName.trim();
    user.updateProfile(trimmedName, locale, clock.instant());
    return users.saveAndFlush(user);
  }

  /** Changes the password and signs out every session. */
  @Transactional
  public void changePassword(UUID userId, String currentPassword, String newPassword) {
    User user = find(userId);
    if (!credentials.matches(user, currentPassword)) {
      List<FieldViolation> violations = List.of(new FieldViolation("currentPassword", "INVALID"));
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "Current password is wrong", violations);
    }
    String hash = credentials.hashNewPassword(newPassword, user.getEmail(), "newPassword");
    user.changePassword(hash, clock.instant());
    refreshTokens.revokeAll(userId);
  }

  private User find(UUID userId) {
    return users
        .findById(userId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
  }
}
