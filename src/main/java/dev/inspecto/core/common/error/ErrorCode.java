package dev.inspecto.core.common.error;

import java.util.Locale;
import org.springframework.http.HttpStatus;

/** Machine-readable error codes of the API (platform/api/common.yaml, ErrorCode). */
public enum ErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
  TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
  FORBIDDEN(HttpStatus.FORBIDDEN),
  NOT_FOUND(HttpStatus.NOT_FOUND),
  VERSION_CONFLICT(HttpStatus.CONFLICT),
  EMAIL_TAKEN(HttpStatus.CONFLICT),
  INVALID_STATE_TRANSITION(HttpStatus.CONFLICT),
  ACCOUNT_LOCKED(HttpStatus.LOCKED),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
  DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

  private final HttpStatus status;

  ErrorCode(HttpStatus status) {
    this.status = status;
  }

  /** HTTP status returned for this code. */
  public HttpStatus status() {
    return status;
  }

  /** Last segment of the problem type URI, for example {@code validation-error}. */
  public String slug() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
