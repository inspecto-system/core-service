package dev.inspecto.core.common.error;

import dev.inspecto.core.common.web.RequestIdFilter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Renders every error as RFC 9457 problem details with {@code code} and {@code requestId}. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private static final String TYPE_BASE = "https://inspecto.dev/problems/";

  /** Bean Validation constraint name → field error code of the API. */
  private static final Map<String, String> CONSTRAINT_CODES =
      Map.of(
          "NotBlank", "NOT_BLANK",
          "NotNull", "NOT_BLANK",
          "NotEmpty", "NOT_BLANK",
          "Size", "INVALID_LENGTH",
          "Email", "INVALID_FORMAT",
          "Pattern", "INVALID_FORMAT",
          "Min", "OUT_OF_RANGE",
          "Max", "OUT_OF_RANGE");

  /** Builds problem details for the given error code. */
  public static ProblemDetail problem(ErrorCode code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
    problem.setTitle(code.status().getReasonPhrase());
    enrich(problem, code);
    return problem;
  }

  @ExceptionHandler(ApiException.class)
  ProblemDetail handleApiException(ApiException ex) {
    ProblemDetail problem = problem(ex.code(), ex.getMessage());
    if (!ex.violations().isEmpty()) {
      problem.setProperty("errors", ex.violations());
    }
    return problem;
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
    return problem(ErrorCode.VERSION_CONFLICT, "The resource was changed by another request");
  }

  @ExceptionHandler(AuthenticationException.class)
  ProblemDetail handleAuthentication(AuthenticationException ex) {
    String message = ex.getMessage();
    boolean expired = message != null && message.contains("expired");
    ErrorCode code = expired ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHENTICATED;
    return problem(code, "Authentication is required");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    return problem(ErrorCode.FORBIDDEN, "The operation is not allowed");
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception ex) {
    LOG.error("Unexpected error", ex);
    return problem(ErrorCode.INTERNAL_ERROR, "Unexpected error");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldViolation> violations = new ArrayList<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      violations.add(toViolation(error));
    }
    ProblemDetail problem = problem(ErrorCode.VALIDATION_ERROR, "Request contains invalid fields");
    problem.setProperty("errors", violations);
    return ResponseEntity.badRequest().body(problem);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    if (body instanceof ProblemDetail problem) {
      enrich(problem, codeFor(statusCode));
    }
    return super.handleExceptionInternal(ex, body, headers, statusCode, request);
  }

  private static void enrich(ProblemDetail problem, ErrorCode code) {
    problem.setType(URI.create(TYPE_BASE + code.slug()));
    problem.setProperty("code", code.name());
    problem.setProperty("requestId", RequestIdFilter.current());
  }

  private static ErrorCode codeFor(HttpStatusCode status) {
    if (status.value() == HttpStatus.NOT_FOUND.value()) {
      return ErrorCode.NOT_FOUND;
    }
    return status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.VALIDATION_ERROR;
  }

  private static FieldViolation toViolation(FieldError error) {
    String constraint = error.getCode() == null ? "" : error.getCode();
    String code = CONSTRAINT_CODES.getOrDefault(constraint, "INVALID");
    return new FieldViolation(error.getField(), code);
  }
}
