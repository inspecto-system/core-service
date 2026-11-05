package dev.inspecto.core.dictionary;

import dev.inspecto.core.dictionary.DictionaryDtos.ChecklistTemplateResponse;
import dev.inspecto.core.dictionary.DictionaryDtos.CreateOwnCategoryRequest;
import dev.inspecto.core.dictionary.DictionaryDtos.DefectCategoryResponse;
import dev.inspecto.core.dictionary.DictionaryDtos.SeverityLevelResponse;
import dev.inspecto.core.dictionary.DictionaryDtos.UpdateOwnCategoryRequest;
import dev.inspecto.core.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Dictionaries for every signed-in user (platform/api/core.yaml, tag dictionaries). */
@RestController
@RequestMapping("/api/v1")
public class DictionaryController {

  private static final String MANAGE_OWN = "hasAnyRole('INSPECTOR', 'ADMIN')";

  private final DefectCategoryService categories;
  private final ChecklistTemplateRepository templates;

  /** Creates the controller. */
  public DictionaryController(
      DefectCategoryService categories, ChecklistTemplateRepository templates) {
    this.categories = categories;
    this.templates = templates;
  }

  /** System and own categories. */
  @GetMapping("/defect-categories")
  public List<DefectCategoryResponse> listCategories(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language,
      @RequestParam(defaultValue = "false") boolean includeInactive) {
    AppLocale locale = AppLocale.fromAcceptLanguage(language);
    return categories.list(CurrentUser.id(jwt), includeInactive, locale).stream()
        .map(category -> DefectCategoryResponse.of(category, locale))
        .toList();
  }

  /** Creates an own category. */
  @PostMapping("/defect-categories")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(MANAGE_OWN)
  public DefectCategoryResponse createOwnCategory(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateOwnCategoryRequest request) {
    DefectCategory category =
        categories.createOwn(CurrentUser.id(jwt), request.name().trim(), request.elementGroup());
    return DefectCategoryResponse.of(category, AppLocale.RU);
  }

  /** Renames or (de)activates an own category. */
  @PatchMapping("/defect-categories/{categoryId}")
  @PreAuthorize(MANAGE_OWN)
  public DefectCategoryResponse updateOwnCategory(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID categoryId,
      @Valid @RequestBody UpdateOwnCategoryRequest request) {
    String name = request.name() == null ? null : request.name().trim();
    DefectCategory category =
        categories.updateOwn(categoryId, CurrentUser.id(jwt), name, request.active());
    return DefectCategoryResponse.of(category, AppLocale.RU);
  }

  /** Deletes an own category. */
  @DeleteMapping("/defect-categories/{categoryId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize(MANAGE_OWN)
  public void deleteOwnCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID categoryId) {
    categories.deleteOwn(categoryId, CurrentUser.id(jwt));
  }

  /** Checklist templates with items. */
  @GetMapping("/checklist-templates")
  public List<ChecklistTemplateResponse> listTemplates(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
    AppLocale locale = AppLocale.fromAcceptLanguage(language);
    return templates.findVisible(CurrentUser.id(jwt)).stream()
        .map(template -> ChecklistTemplateResponse.of(template, locale))
        .toList();
  }

  /** Severity scale from least to most severe. */
  @GetMapping("/dictionaries/severities")
  public List<SeverityLevelResponse> listSeverities(
      @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
    AppLocale locale = AppLocale.fromAcceptLanguage(language);
    return Arrays.stream(Severity.values())
        .map(severity -> SeverityLevelResponse.of(severity, locale))
        .toList();
  }
}
