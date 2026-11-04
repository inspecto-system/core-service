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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Password rules (SECURITY.md §2, NIST SP 800-63B): length is validated on the request, here the
 * password is checked against common passwords and the user's own email.
 */
@Component
public class PasswordPolicy {

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
    String normalized = password.toLowerCase(Locale.ROOT);
    boolean common = commonPasswords.contains(normalized);
    boolean sameAsEmail = email != null && normalized.equals(email.toLowerCase(Locale.ROOT));
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
          .map(line -> line.toLowerCase(Locale.ROOT))
          .collect(Collectors.toUnmodifiableSet());
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot load " + COMMON_PASSWORDS, e);
    }
  }
}
