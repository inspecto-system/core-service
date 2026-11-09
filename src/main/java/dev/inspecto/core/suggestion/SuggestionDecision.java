package dev.inspecto.core.suggestion;

import dev.inspecto.core.suggestion.MlClient.Prediction;
import java.util.List;

/** What the user did with a suggestion (usefulness metric of ML_DOCUMENTATION.md §5). */
public enum SuggestionDecision {
  /** Chose the first suggested category. */
  ACCEPTED_TOP1,
  /** Chose another suggested category. */
  ACCEPTED_OTHER,
  /** Chose a category that was not suggested (including the user's own categories). */
  REJECTED;

  /**
   * Decision for the chosen category.
   *
   * @param chosenCode code of the chosen category, {@code null} for a user's own category
   */
  public static SuggestionDecision of(List<Prediction> shown, String chosenCode) {
    if (chosenCode == null || shown.isEmpty()) {
      return REJECTED;
    }
    if (shown.getFirst().code().equals(chosenCode)) {
      return ACCEPTED_TOP1;
    }
    boolean suggested = shown.stream().anyMatch(p -> p.code().equals(chosenCode));
    return suggested ? ACCEPTED_OTHER : REJECTED;
  }
}
