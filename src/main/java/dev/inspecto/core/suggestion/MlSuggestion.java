package dev.inspecto.core.suggestion;

import dev.inspecto.core.suggestion.MlClient.Prediction;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/** What ml-service suggested for a photo and what the user then chose (table ml_suggestions). */
@Entity
@Table(name = "ml_suggestions")
public class MlSuggestion {

  @Id private UUID id;

  @Column(name = "photo_id", nullable = false, updatable = false)
  private UUID photoId;

  @Column(name = "requested_by", nullable = false, updatable = false)
  private UUID requestedBy;

  @Column(name = "model_version", nullable = false, updatable = false)
  private String modelVersion;

  @Convert(converter = PredictionsConverter.class)
  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  @ColumnTransformer(write = "?::jsonb")
  private List<Prediction> predictions;

  @Column(name = "defect_id")
  private UUID defectId;

  @Column(name = "chosen_category_id")
  private UUID chosenCategoryId;

  @Enumerated(EnumType.STRING)
  private SuggestionDecision decision;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "decided_at")
  private Instant decidedAt;

  protected MlSuggestion() {
    // for JPA
  }

  /** A new suggestion without a decision. */
  public MlSuggestion(UUID id, UUID photoId, UUID requestedBy, Shown shown, Instant now) {
    this.id = id;
    this.photoId = photoId;
    this.requestedBy = requestedBy;
    this.modelVersion = shown.modelVersion();
    this.predictions = List.copyOf(shown.predictions());
    this.createdAt = now;
  }

  /** Records the category the user chose for the defect created with this suggestion. */
  public void decide(UUID defect, UUID chosenCategory, String chosenCode, Instant now) {
    this.defectId = defect;
    this.chosenCategoryId = chosenCategory;
    this.decision = SuggestionDecision.of(predictions, chosenCode);
    this.decidedAt = now;
  }

  /** Whether the user has already used the suggestion for a defect. */
  public boolean isDecided() {
    return decision != null;
  }

  public UUID getId() {
    return id;
  }

  public SuggestionDecision getDecision() {
    return decision;
  }

  public UUID getDefectId() {
    return defectId;
  }

  /**
   * Suggestions shown to the user.
   *
   * @param modelVersion version of the model that made them
   * @param predictions the shown categories by probability
   */
  public record Shown(String modelVersion, List<Prediction> predictions) {

    /** Defensive copy. */
    public Shown {
      predictions = List.copyOf(predictions);
    }
  }
}
