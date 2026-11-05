package dev.inspecto.core.dictionary;

import dev.inspecto.core.dictionary.DictionaryDtos.CreateSystemCategoryRequest;
import dev.inspecto.core.dictionary.DictionaryDtos.DefectCategoryResponse;
import dev.inspecto.core.dictionary.DictionaryDtos.UpdateSystemCategoryRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** System defect categories, managed by administrators only (ADR-009, ADR-017). */
@RestController
@RequestMapping("/api/v1/admin/defect-categories")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController {

  private final DefectCategoryService categories;

  /** Creates the controller. */
  public AdminCategoryController(DefectCategoryService categories) {
    this.categories = categories;
  }

  /** Creates a system category. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DefectCategoryResponse create(@Valid @RequestBody CreateSystemCategoryRequest request) {
    DefectCategory category =
        categories.createSystem(request.code(), request.names().trimmed(), request.elementGroup());
    return DefectCategoryResponse.of(category, AppLocale.RU);
  }

  /** Updates or (de)activates a system category. */
  @PatchMapping("/{categoryId}")
  public DefectCategoryResponse update(
      @PathVariable UUID categoryId, @Valid @RequestBody UpdateSystemCategoryRequest request) {
    LocalizedText names = request.names() == null ? null : request.names().trimmed();
    DefectCategory category =
        categories.updateSystem(categoryId, names, request.elementGroup(), request.active());
    return DefectCategoryResponse.of(category, AppLocale.RU);
  }
}
