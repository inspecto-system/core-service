package dev.inspecto.core.dictionary;

import java.util.UUID;

/**
 * Tells whether defects refer to a category. Implemented by the inspection module, which depends on
 * this one; the interface keeps the dependency one-way.
 */
@FunctionalInterface
public interface CategoryUsage {

  /** Whether any defect has the category. */
  boolean isCategoryUsed(UUID categoryId);
}
