package dev.inspecto.core.dictionary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LocalizationTest {

  @ParameterizedTest(name = "\"{0}\" -> {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        "en-US,en;q=0.9|EN",
        "ru-RU,ru;q=0.9,en;q=0.8|RU",
        "de-DE,en;q=0.5|EN",
        "de-DE|RU",
        "not a ; valid = header|RU",
        "''|RU"
      })
  void shouldPickSupportedLocaleFromAcceptLanguage(String header, AppLocale expected) {
    assertThat(AppLocale.fromAcceptLanguage(header)).isEqualTo(expected);
  }

  @Test
  void shouldDefaultToRussianWithoutHeader() {
    assertThat(AppLocale.fromAcceptLanguage(null)).isEqualTo(AppLocale.RU);
  }

  @Test
  void shouldFallBackToRussianWithoutTranslation() {
    LocalizedText text = new LocalizedText("Трещина", null);

    assertThat(text.in(AppLocale.EN)).isEqualTo("Трещина");
    assertThat(new LocalizedText("Трещина", " ").in(AppLocale.EN)).isEqualTo("Трещина");
    assertThat(new LocalizedText("Трещина", "Crack").in(AppLocale.EN)).isEqualTo("Crack");
    assertThat(new LocalizedText("Трещина", "Crack").in(AppLocale.RU)).isEqualTo("Трещина");
  }

  @Test
  void shouldTrimAndDropBlankTranslation() {
    assertThat(new LocalizedText(" Трещина ", "  ").trimmed())
        .isEqualTo(new LocalizedText("Трещина", null));
    assertThat(new LocalizedText("Трещина", " Crack ").trimmed())
        .isEqualTo(new LocalizedText("Трещина", "Crack"));
  }

  @Test
  void shouldNameSeverityInLocale() {
    assertThat(Severity.MAJOR.name(AppLocale.RU)).isEqualTo("Значительный");
    assertThat(Severity.MAJOR.name(AppLocale.EN)).isEqualTo("Major");
  }
}
