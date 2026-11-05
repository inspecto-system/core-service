package dev.inspecto.core.dictionary;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Request and response bodies of the dictionary API (platform/api/core.yaml, dictionaries). */
public final class DictionaryDtos {

  /** At least one non-whitespace character; PATCH fields cannot use {@code @NotBlank}. */
  private static final String NOT_BLANK = "(?s).*\\S.*";

  private DictionaryDtos() {}

  /** Category with its name in the requested locale. */
  public record DefectCategoryResponse(
      UUID id,
      String code,
      String name,
      LocalizedText names,
      boolean system,
      ElementGroup elementGroup,
      boolean active) {

    /** Maps an entity to the response. */
    public static DefectCategoryResponse of(DefectCategory category, AppLocale locale) {
      return new DefectCategoryResponse(
          category.getId(),
          category.getCode(),
          category.getNames().in(locale),
          category.getNames(),
          category.isSystem(),
          category.getElementGroup(),
          category.isActive());
    }
  }

  /** New own category. */
  public record CreateOwnCategoryRequest(
      @NotBlank @Size(max = 100) String name, ElementGroup elementGroup) {}

  /** Rename or (de)activate an own category; absent fields keep their values. */
  public record UpdateOwnCategoryRequest(
      @Size(min = 1, max = 100) @Pattern(regexp = NOT_BLANK) String name, Boolean active) {}

  /** New system category (ADMIN). */
  public record CreateSystemCategoryRequest(
      @NotNull @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$") String code,
      @NotNull @Valid LocalizedText names,
      ElementGroup elementGroup) {}

  /** Update of a system category (ADMIN); absent fields keep their values. */
  public record UpdateSystemCategoryRequest(
      @Valid LocalizedText names, ElementGroup elementGroup, Boolean active) {}

  /** Checklist template in the requested locale. */
  public record ChecklistTemplateResponse(
      UUID id, String code, String name, List<ChecklistTemplateItemResponse> items) {

    /** Copies the item list. */
    public ChecklistTemplateResponse {
      items = List.copyOf(items);
    }

    /** Maps an entity to the response. */
    public static ChecklistTemplateResponse of(ChecklistTemplate template, AppLocale locale) {
      return new ChecklistTemplateResponse(
          template.getId(),
          template.getCode(),
          template.getNames().in(locale),
          template.getItems().stream()
              .map(item -> ChecklistTemplateItemResponse.of(item, locale))
              .toList());
    }
  }

  /** Checklist template item in the requested locale. */
  public record ChecklistTemplateItemResponse(
      UUID id, ElementGroup elementGroup, String title, int sortOrder) {

    static ChecklistTemplateItemResponse of(ChecklistTemplateItem item, AppLocale locale) {
      return new ChecklistTemplateItemResponse(
          item.getId(), item.getElementGroup(), item.getTitles().in(locale), item.getSortOrder());
    }
  }

  /** Severity level in the requested locale. */
  public record SeverityLevelResponse(Severity code, String name, int rank) {

    /** Maps a severity to the response. */
    public static SeverityLevelResponse of(Severity severity, AppLocale locale) {
      return new SeverityLevelResponse(severity, severity.name(locale), severity.rank());
    }
  }
}
