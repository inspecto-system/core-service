package dev.inspecto.core.common.error;

/** Optimistic locking check of modifying requests (docs/API_DOCUMENTATION.md §1). */
public final class OptimisticVersion {

  private OptimisticVersion() {}

  /**
   * Checks that the client based its change on the current version.
   *
   * @throws ApiException {@code VERSION_CONFLICT} when the versions differ
   */
  public static void require(long current, long expected) {
    if (current != expected) {
      throw new ApiException(ErrorCode.VERSION_CONFLICT, "The resource was changed meanwhile");
    }
  }
}
