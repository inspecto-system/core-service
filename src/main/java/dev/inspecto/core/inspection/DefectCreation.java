package dev.inspecto.core.inspection;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.common.error.FieldViolation;
import dev.inspecto.core.common.id.UuidV7;
import dev.inspecto.core.inspection.DefectService.DefectView;
import dev.inspecto.core.inspection.DefectService.NewDefect;
import dev.inspecto.core.suggestion.CategorySuggestionService;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creation of a defect with an optional {@code Idempotency-Key} (platform/api core 0.5.0): a
 * repeated request with the same key of the same user returns the defect created by the first one,
 * so the Android offline queue can repeat a request whose answer was lost (T-308).
 */
@Service
public class DefectCreation {

  /** Header name, also the field of a validation error. */
  public static final String HEADER = "Idempotency-Key";

  private static final int MIN_KEY_LENGTH = 8;
  private static final int MAX_KEY_LENGTH = 64;

  private final DefectService defects;
  private final DefectIdempotencyKeyRepository keys;
  private final CategorySuggestionService suggestions;
  private final Clock clock;

  /** Creates the service. */
  public DefectCreation(
      DefectService defects,
      DefectIdempotencyKeyRepository keys,
      CategorySuggestionService suggestions,
      Clock clock) {
    this.defects = defects;
    this.keys = keys;
    this.suggestions = suggestions;
    this.clock = clock;
  }

  /**
   * Creates the defect, or returns the one created earlier with the same key.
   *
   * @param key value of the header, or null without it
   * @throws ApiException {@code VALIDATION_ERROR} for a key of a wrong length or an unusable {@code
   *     suggestionId}; the errors of {@link DefectService#create}
   */
  @Transactional
  public DefectView create(UUID inspectionId, UUID userId, NewDefect request, String key) {
    if (key == null) {
      return createNew(inspectionId, userId, request);
    }
    requireValid(key);
    keys.lock(userId + ":" + key);
    Optional<DefectIdempotencyKey> earlier = keys.findByUserIdAndKey(userId, key);
    if (earlier.isPresent()) {
      return defects.get(earlier.get().getDefectId(), userId);
    }
    DefectView created = createNew(inspectionId, userId, request);
    keys.save(
        new DefectIdempotencyKey(
            UuidV7.generate(clock), userId, key, created.defect().getId(), clock.instant()));
    return created;
  }

  /** The defect and, when it was created with an ML suggestion, the user's decision on it. */
  private DefectView createNew(UUID inspectionId, UUID userId, NewDefect request) {
    DefectView created = defects.create(inspectionId, userId, request);
    if (request.suggestionId() != null) {
      suggestions.recordDecision(
          request.suggestionId(), userId, created.defect().getId(), request.fields().categoryId());
    }
    return created;
  }

  private static void requireValid(String key) {
    if (key.length() < MIN_KEY_LENGTH || key.length() > MAX_KEY_LENGTH) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR,
          "Idempotency-Key must have 8 to 64 characters",
          List.of(new FieldViolation(HEADER, "INVALID_LENGTH")));
    }
  }
}
