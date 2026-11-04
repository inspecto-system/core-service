package dev.inspecto.core.common.error;

import java.util.List;

/** Business error rendered as RFC 9457 problem details with a machine-readable code. */
public class ApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode code;
  private final transient List<FieldViolation> violations;

  /** Creates an error without field violations. */
  public ApiException(ErrorCode code, String detail) {
    this(code, detail, List.of());
  }

  /** Creates an error caused by another exception. */
  public ApiException(ErrorCode code, String detail, Throwable cause) {
    super(detail, cause);
    this.code = code;
    this.violations = List.of();
  }

  /** Creates an error with field violations. */
  public ApiException(ErrorCode code, String detail, List<FieldViolation> violations) {
    super(detail);
    this.code = code;
    this.violations = List.copyOf(violations);
  }

  public ErrorCode code() {
    return code;
  }

  public List<FieldViolation> violations() {
    return violations;
  }
}
