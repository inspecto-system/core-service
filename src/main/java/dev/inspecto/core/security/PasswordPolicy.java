package dev.inspecto.core.security;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.common.error.FieldViolation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Password rules (SECURITY.md §2, NIST SP 800-63B): length is validated on the request, here the
 * password is checked against common passwords and the user's own email.
 */
@Component
public final class PasswordPolicy {

  private static final String COMMON_PASSWORDS = "security/common-passwords.txt";

  private final Set<String> commonPasswords;

  /** Loads the list of common passwords from the classpath. */
  public PasswordPolicy() {
    this.commonPasswords = load();
  }

  /**
   * Rejects weak passwords.
   *
   * @param field request field name used in the error
   * @throws ApiException {@code VALIDATION_ERROR} with code {@code WEAK_PASSWORD}
   */
  public void check(String password, String email, String field) {
    boolean common = commonPasswords.contains(password);
    boolean sameAsEmail =
        email != null && String.CASE_INSENSITIVE_ORDER.compare(password, email) == 0;
    if (common || sameAsEmail) {
      List<FieldViolation> violations = List.of(new FieldViolation(field, "WEAK_PASSWORD"));
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "Password is too weak", violations);
    }
  }

  private static Set<String> load() {
    try (InputStream in = new ClassPathResource(COMMON_PASSWORDS).getInputStream();
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .collect(
              Collectors.collectingAndThen(
                  Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)),
                  Collections::unmodifiableSortedSet));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot load " + COMMON_PASSWORDS, e);
    }
  }
}
