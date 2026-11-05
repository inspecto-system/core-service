package dev.inspecto.core.dictionary;

import java.util.List;
import java.util.Locale;

/** Locales the API translates dictionaries into (ADR-019). */
public enum AppLocale {
  RU("ru"),
  EN("en");

  private static final List<String> TAGS = List.of("ru", "en");

  private final String tag;

  AppLocale(String tag) {
    this.tag = tag;
  }

  /** Java locale, for example for collation. */
  public Locale toLocale() {
    return Locale.forLanguageTag(tag);
  }

  /**
   * Best match for an {@code Accept-Language} header such as {@code en-US,en;q=0.9}; Russian when
   * the header is absent, malformed or names no supported language.
   */
  public static AppLocale fromAcceptLanguage(String header) {
    if (header == null || header.isBlank()) {
      return RU;
    }
    try {
      String match = Locale.lookupTag(Locale.LanguageRange.parse(header), TAGS);
      return EN.tag.equals(match) ? EN : RU;
    } catch (IllegalArgumentException e) {
      return RU;
    }
  }
}
