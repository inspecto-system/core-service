package dev.inspecto.core.inspection;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.dictionary.AppLocale;
import dev.inspecto.core.dictionary.DefectCategory;
import dev.inspecto.core.dictionary.DefectCategoryRepository;
import dev.inspecto.core.property.Property;
import dev.inspecto.core.property.PropertyRepository;
import dev.inspecto.core.user.User;
import dev.inspecto.core.user.UserRepository;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Property, inspector and category names of a report, which live outside the inspection. */
@Component
public class ReportContext {

  private final PropertyRepository properties;
  private final UserRepository users;
  private final DefectCategoryRepository categories;

  /** Creates the component. */
  public ReportContext(
      PropertyRepository properties, UserRepository users, DefectCategoryRepository categories) {
    this.properties = properties;
    this.users = users;
    this.categories = categories;
  }

  /** Property of the inspection; a deleted one ends the report with {@code NOT_FOUND}. */
  public Property property(Inspection inspection) {
    return properties.findById(inspection.getPropertyId()).orElseThrow(ReportContext::notFound);
  }

  /** Name of the inspector, empty when the account no longer exists. */
  public String inspectorName(Inspection inspection) {
    return users.findById(inspection.getInspectorId()).map(User::getFullName).orElse("");
  }

  /** Names of the categories the owner sees, in the language of the report. */
  public Map<UUID, String> categoryNames(UUID ownerId, AppLocale locale) {
    return categories.findVisible(ownerId).stream()
        .collect(Collectors.toMap(DefectCategory::getId, c -> c.getNames().in(locale)));
  }

  static ApiException notFound() {
    return new ApiException(ErrorCode.NOT_FOUND, "Inspection not found");
  }
}
