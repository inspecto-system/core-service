package dev.inspecto.core.dictionary;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.common.id.UuidV7;
import java.text.Collator;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** System and own defect categories (T-104, ADR-017). */
@Service
public class DefectCategoryService {

  private final DefectCategoryRepository categories;
  private final CategoryUsage usage;
  private final Clock clock;

  /** Creates the service. */
  public DefectCategoryService(
      DefectCategoryRepository categories, CategoryUsage usage, Clock clock) {
    this.categories = categories;
    this.usage = usage;
    this.clock = clock;
  }

  /** System categories, then the user's own ones, each group sorted by name in the locale. */
  @Transactional(readOnly = true)
  public List<DefectCategory> list(UUID userId, boolean includeInactive, AppLocale locale) {
    Collator collator = Collator.getInstance(locale.toLocale());
    Comparator<DefectCategory> order =
        Comparator.comparing((DefectCategory c) -> !c.isSystem())
            .thenComparing(c -> c.getNames().in(locale), collator);
    return categories.findVisible(userId).stream()
        .filter(c -> includeInactive || c.isActive())
        .sorted(order)
        .toList();
  }

  /** Creates a category visible only to the user. */
  @Transactional
  public DefectCategory createOwn(UUID userId, String name, ElementGroup group) {
    UUID id = UuidV7.generate(clock);
    return categories.save(DefectCategory.own(id, userId, name, group, clock.instant()));
  }

  /** Renames or (de)activates an own category. */
  @Transactional
  public DefectCategory updateOwn(UUID categoryId, UUID userId, String name, Boolean active) {
    DefectCategory category = own(categoryId, userId);
    LocalizedText names = name == null ? null : new LocalizedText(name, null);
    category.update(names, null, active, clock.instant());
    return categories.saveAndFlush(category);
  }

  /**
   * Deletes an own category that no defect has; a used category can be deactivated instead.
   *
   * @throws ApiException {@code RESOURCE_IN_USE} when a defect has the category
   */
  @Transactional
  public void deleteOwn(UUID categoryId, UUID userId) {
    DefectCategory category = own(categoryId, userId);
    if (usage.isCategoryUsed(categoryId)) {
      throw new ApiException(ErrorCode.RESOURCE_IN_USE, "The category is used by defects");
    }
    categories.delete(category);
  }

  /**
   * Creates a system category (ADMIN).
   *
   * @throws ApiException {@code CATEGORY_CODE_TAKEN} when another system category has the code
   */
  @Transactional
  public DefectCategory createSystem(String code, LocalizedText names, ElementGroup group) {
    if (categories.existsByCodeAndOwnerIdIsNull(code)) {
      throw codeTaken();
    }
    DefectCategory category =
        DefectCategory.system(UuidV7.generate(clock), code, names, group, clock.instant());
    try {
      return categories.saveAndFlush(category);
    } catch (DataIntegrityViolationException e) {
      // A concurrent request created the same code after the check above.
      throw new ApiException(ErrorCode.CATEGORY_CODE_TAKEN, "Category code is taken", e);
    }
  }

  /** Updates or (de)activates a system category (ADMIN). */
  @Transactional
  public DefectCategory updateSystem(
      UUID categoryId, LocalizedText names, ElementGroup group, Boolean active) {
    DefectCategory category =
        categories
            .findByIdAndOwnerIdIsNull(categoryId)
            .orElseThrow(DefectCategoryService::notFound);
    category.update(names, group, active, clock.instant());
    return categories.saveAndFlush(category);
  }

  private DefectCategory own(UUID categoryId, UUID userId) {
    return categories
        .findByIdAndOwnerId(categoryId, userId)
        .orElseThrow(DefectCategoryService::notFound);
  }

  private static ApiException notFound() {
    return new ApiException(ErrorCode.NOT_FOUND, "Category not found");
  }

  private static ApiException codeTaken() {
    return new ApiException(ErrorCode.CATEGORY_CODE_TAKEN, "Category code is taken");
  }
}
