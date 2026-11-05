package dev.inspecto.core.common.logging;

import java.util.regex.Pattern;
import org.springframework.boot.json.JsonWriter.MemberPath;
import org.springframework.boot.json.JsonWriter.Members;
import org.springframework.boot.json.JsonWriter.ValueProcessor;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Masks values of structured log members whose name suggests a secret (SECURITY.md §5): MDC entries
 * and key-value pairs named like {@code password}, {@code token}, {@code secret}, {@code
 * authorization} or {@code cookie}. Messages themselves never contain secrets by convention.
 */
public class SensitiveDataMasking implements StructuredLoggingJsonMembersCustomizer<Object> {

  /** Replacement of a masked value. */
  public static final String MASK = "***";

  private static final Pattern SENSITIVE =
      Pattern.compile("password|token|secret|authorization|cookie", Pattern.CASE_INSENSITIVE);

  @Override
  public void customize(Members<Object> members) {
    members.applyingValueProcessor(
        ValueProcessor.of(value -> MASK).whenHasPath(SensitiveDataMasking::isSensitive));
  }

  static boolean isSensitive(MemberPath path) {
    String name = path.name();
    return name != null && SENSITIVE.matcher(name).find();
  }
}
