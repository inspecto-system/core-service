package dev.inspecto.core.dictionary;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Translations of a dictionary name (ADR-019); Russian is mandatory, English optional. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LocalizedText(@NotBlank @Size(max = 100) String ru, @Size(max = 100) String en) {

  /** Text in the given locale, falling back to Russian when there is no translation. */
  public String in(AppLocale locale) {
    return locale == AppLocale.EN && en != null && !en.isBlank() ? en : ru;
  }

  /** Copy without surrounding whitespace; a blank English text is dropped. */
  public LocalizedText trimmed() {
    String english = en == null || en.isBlank() ? null : en.trim();
    return new LocalizedText(ru.trim(), english);
  }
}
