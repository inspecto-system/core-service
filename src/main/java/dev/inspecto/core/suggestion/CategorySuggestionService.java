package dev.inspecto.core.suggestion;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.common.error.FieldViolation;
import dev.inspecto.core.common.id.UuidV7;
import dev.inspecto.core.dictionary.AppLocale;
import dev.inspecto.core.dictionary.DefectCategory;
import dev.inspecto.core.dictionary.DefectCategoryRepository;
import dev.inspecto.core.media.MediaClient;
import dev.inspecto.core.media.MediaClient.PhotoMetadata;
import dev.inspecto.core.media.MediaUnavailableException;
import dev.inspecto.core.suggestion.MlClient.Classification;
import dev.inspecto.core.suggestion.MlClient.Prediction;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Category suggestions for a photo (T-602, ADR-016): ml-service proposes, the user decides.
 *
 * <p>The photo must be one the user uploaded (ids from clients are not trusted, ADR-009). Any
 * failure of media-service or ml-service gives an empty answer instead of an error: suggestions
 * only speed up the work and must never block creating a defect (T-604).
 */
@Service
public class CategorySuggestionService {

  private static final Logger LOG = LoggerFactory.getLogger(CategorySuggestionService.class);

  private final MediaClient media;
  private final MlClient ml;
  private final DefectCategoryRepository categories;
  private final MlSuggestionRepository suggestions;
  private final Clock clock;

  /** Creates the service. */
  public CategorySuggestionService(
      MediaClient media,
      MlClient ml,
      DefectCategoryRepository categories,
      MlSuggestionRepository suggestions,
      Clock clock) {
    this.media = media;
    this.ml = ml;
    this.categories = categories;
    this.suggestions = suggestions;
    this.clock = clock;
  }

  /**
   * Suggested system categories for the photo; empty when there is no confident answer.
   *
   * @throws ApiException {@code NOT_FOUND} when the photo does not exist or is someone else's
   */
  public SuggestionResult suggest(UUID userId, UUID photoId, AppLocale locale) {
    Optional<PhotoMetadata> photo;
    try {
      photo = media.metadata(photoId);
    } catch (MediaUnavailableException e) {
      // Without media-service the owner cannot be checked and ml-service could not read the photo
      // either; a 404 would wrongly claim the photo is missing, so the answer is just empty.
      LOG.warn("media-service unavailable, no suggestion", e);
      return SuggestionResult.EMPTY;
    }
    if (photo.map(PhotoMetadata::ownerId).filter(userId::equals).isEmpty()) {
      throw new ApiException(ErrorCode.NOT_FOUND, "Photo not found");
    }
    return ml.classify(photoId)
        .map(answer -> remember(userId, photoId, answer, locale))
        .orElse(SuggestionResult.EMPTY);
  }

  /** Known active system categories of the answer, saved as a suggestion when there are any. */
  private SuggestionResult remember(
      UUID userId, UUID photoId, Classification answer, AppLocale locale) {
    List<SuggestedCategory> items = new ArrayList<>();
    for (Prediction prediction : answer.predictions()) {
      categories
          .findByCodeAndOwnerIdIsNull(prediction.code())
          .filter(DefectCategory::isActive)
          .ifPresent(category -> items.add(SuggestedCategory.of(category, prediction, locale)));
    }
    if (items.isEmpty()) {
      return SuggestionResult.EMPTY;
    }
    List<Prediction> shown = items.stream().map(SuggestedCategory::prediction).toList();
    MlSuggestion saved =
        suggestions.save(
            new MlSuggestion(
                UuidV7.generate(clock),
                photoId,
                userId,
                new MlSuggestion.Shown(answer.modelVersion(), shown),
                clock.instant()));
    return new SuggestionResult(saved.getId(), answer.modelVersion(), items);
  }

  /**
   * Records which category the user chose for the defect created with the suggestion; runs in the
   * transaction that creates the defect.
   *
   * @throws ApiException {@code VALIDATION_ERROR} ({@code suggestionId: UNKNOWN_ID}) for a
   *     suggestion that does not exist, is someone else's or was already used
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordDecision(UUID suggestionId, UUID userId, UUID defectId, UUID categoryId) {
    MlSuggestion suggestion =
        suggestions
            .findByIdAndRequestedBy(suggestionId, userId)
            .filter(s -> !s.isDecided())
            .orElseThrow(() ->
                new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    "Request contains invalid fields",
                    List.of(new FieldViolation("suggestionId", "UNKNOWN_ID"))));
    String chosenCode = categories.findById(categoryId).map(DefectCategory::getCode).orElse(null);
    suggestion.decide(defectId, categoryId, chosenCode, clock.instant());
  }

  /**
   * Suggestions for the client.
   *
   * @param suggestionId id to pass when creating the defect, {@code null} without suggestions
   * @param modelVersion version of the model, {@code null} without suggestions
   * @param items up to three categories by probability
   */
  public record SuggestionResult(
      UUID suggestionId, String modelVersion, List<SuggestedCategory> items) {

    /** No suggestion. */
    public static final SuggestionResult EMPTY = new SuggestionResult(null, null, List.of());

    /** Defensive copy. */
    public SuggestionResult {
      items = List.copyOf(items);
    }
  }

  /** A system category with its name in the request language and the model's probability. */
  public record SuggestedCategory(UUID categoryId, String code, String name, double probability) {

    static SuggestedCategory of(DefectCategory category, Prediction prediction, AppLocale locale) {
      return new SuggestedCategory(
          category.getId(),
          category.getCode(),
          category.getNames().in(locale),
          prediction.probability());
    }

    Prediction prediction() {
      return new Prediction(code, probability);
    }
  }
}
