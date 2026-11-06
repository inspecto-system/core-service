package dev.inspecto.core.media;

/** media-service could not be reached or answered unexpectedly. */
public class MediaUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public MediaUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }

  /** Creates the exception without a cause. */
  public MediaUnavailableException(String message) {
    super(message);
  }
}
