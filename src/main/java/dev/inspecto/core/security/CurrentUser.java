package dev.inspecto.core.security;

import java.util.Objects;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Identity of the caller; taken only from the validated access token (SECURITY.md §3). */
public final class CurrentUser {

  private CurrentUser() {}

  /** Identifier of the signed-in user. */
  public static UUID id(Jwt jwt) {
    // The decoder accepts only tokens signed by core, and core always sets the subject.
    return UUID.fromString(Objects.requireNonNull(jwt.getSubject(), "Access token has no subject"));
  }
}
