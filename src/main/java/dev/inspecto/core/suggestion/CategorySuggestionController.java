package dev.inspecto.core.suggestion;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.inspecto.core.dictionary.AppLocale;
import dev.inspecto.core.security.CurrentUser;
import dev.inspecto.core.suggestion.CategorySuggestionService.SuggestedCategory;
import dev.inspecto.core.suggestion.CategorySuggestionService.SuggestionResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /api/v1/category-suggestions} (platform/api/core.yaml, tag suggestions). */
@RestController
@PreAuthorize("hasAnyRole('INSPECTOR', 'ADMIN')")
public class CategorySuggestionController {

  private final CategorySuggestionService suggestions;

  /** Creates the controller. */
  public CategorySuggestionController(CategorySuggestionService suggestions) {
    this.suggestions = suggestions;
  }

  /** Suggested categories for a photo the user uploaded; possibly an empty list. */
  @PostMapping("/api/v1/category-suggestions")
  public SuggestionResponse suggest(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language,
      @Valid @RequestBody SuggestionRequest request) {
    AppLocale locale = AppLocale.fromAcceptLanguage(language);
    return SuggestionResponse.of(
        suggestions.suggest(CurrentUser.id(jwt), request.photoId(), locale));
  }

  /** {@code SuggestionRequest}. */
  public record SuggestionRequest(@NotNull UUID photoId) {}

  /** {@code SuggestionResponse}: without suggestions only {@code items: []}. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SuggestionResponse(
      UUID suggestionId, String modelVersion, List<SuggestedCategory> items) {

    /** Defensive copy. */
    public SuggestionResponse {
      items = List.copyOf(items);
    }

    static SuggestionResponse of(SuggestionResult result) {
      return new SuggestionResponse(result.suggestionId(), result.modelVersion(), result.items());
    }
  }
}
