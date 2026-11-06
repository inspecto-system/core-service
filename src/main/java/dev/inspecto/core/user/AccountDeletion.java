package dev.inspecto.core.user;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.common.error.FieldViolation;
import dev.inspecto.core.outbox.OutboxEventType;
import dev.inspecto.core.outbox.OutboxService;
import dev.inspecto.core.security.CredentialService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletion of an account with all its data (T-303, ADR-018). Rows go in one transaction: the
 * database cascades from the user to sessions, own categories and properties, and from properties
 * to rooms, inspections, defects and observations. Files are removed afterwards by an outbox job.
 */
@Service
public class AccountDeletion {

  private final UserRepository users;
  private final CredentialService credentials;
  private final OutboxService outbox;

  /** Creates the service. */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring injects shared singleton beans; copying them is meaningless")
  public AccountDeletion(
      UserRepository users, CredentialService credentials, OutboxService outbox) {
    this.users = users;
    this.credentials = credentials;
    this.outbox = outbox;
  }

  /**
   * Deletes the account after checking its password.
   *
   * @throws ApiException {@code VALIDATION_ERROR} for a wrong password (as when changing it),
   *     {@code NOT_FOUND} when the account no longer exists
   */
  @Transactional
  public void delete(UUID userId, String password) {
    User user =
        users
            .findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
    if (!credentials.matches(user, password)) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR,
          "Password is wrong",
          List.of(new FieldViolation("password", "INVALID")));
    }
    users.delete(user);
    users.flush();
    outbox.enqueue(OutboxEventType.DELETE_USER_FILES, Map.of("ownerId", userId.toString()));
  }
}
