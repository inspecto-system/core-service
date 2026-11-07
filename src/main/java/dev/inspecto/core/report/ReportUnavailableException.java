package dev.inspecto.core.report;

/** report-service could not be reached or answered unexpectedly. */
public class ReportUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public ReportUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
