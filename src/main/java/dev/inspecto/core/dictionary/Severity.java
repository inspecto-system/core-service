package dev.inspecto.core.dictionary;

/** Fixed severity scale of defects, from least to most severe (ADR-017). */
public enum Severity {
  COSMETIC(1, "Косметический", "Cosmetic"),
  MINOR(2, "Незначительный", "Minor"),
  MAJOR(3, "Значительный", "Major"),
  CRITICAL(4, "Критический", "Critical");

  private final int rank;
  private final String nameRu;
  private final String nameEn;

  Severity(int rank, String nameRu, String nameEn) {
    this.rank = rank;
    this.nameRu = nameRu;
    this.nameEn = nameEn;
  }

  /** Position on the scale, 1 is the least severe. */
  public int rank() {
    return rank;
  }

  /** Name in the given locale. */
  public String name(AppLocale locale) {
    return locale == AppLocale.EN ? nameEn : nameRu;
  }
}
