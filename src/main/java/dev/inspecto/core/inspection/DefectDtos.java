package dev.inspecto.core.inspection;

import dev.inspecto.core.dictionary.ElementGroup;
import dev.inspecto.core.dictionary.Severity;
import dev.inspecto.core.inspection.Defect.DefectFields;
import dev.inspecto.core.inspection.DefectObservation.ObservationEntry;
import dev.inspecto.core.inspection.DefectService.DefectView;
import dev.inspecto.core.inspection.DefectService.NewDefect;
import dev.inspecto.core.inspection.InspectionViews.ObservedDefect;
import dev.inspecto.core.inspection.ObservationService.NewObservation;
import dev.inspecto.core.inspection.OpenDefects.OpenDefect;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** Request and response bodies of the defect and observation API (platform/api/core.yaml). */
public final class DefectDtos {

  /** At least one non-whitespace character; PATCH fields cannot use {@code @NotBlank}. */
  private static final String NOT_BLANK = "(?s).*\\S.*";

  private DefectDtos() {}

  /** New defect with its first observation. */
  public record CreateDefectRequest(
      @NotNull UUID roomId,
      UUID checklistItemId,
      @NotNull UUID categoryId,
      @NotNull ElementGroup elementGroup,
      @NotBlank @Size(max = 200) String title,
      @Size(max = 4000) String description,
      @NotNull Severity severity,
      @Size(max = 2000) String comment) {

    /** Service input with trimmed text. */
    public NewDefect toNewDefect() {
      DefectFields fields =
          new DefectFields(categoryId, title.trim(), trimToNull(description), severity);
      return new NewDefect(roomId, checklistItemId, elementGroup, fields, trimToNull(comment));
    }
  }

  /**
   * Partial defect update. Absent fields keep their values; an explicit JSON {@code null} clears
   * {@code description}, so its setter records whether it was sent.
   */
  public static final class UpdateDefectRequest {

    private UUID categoryId;

    @Size(min = 1, max = 200)
    @Pattern(regexp = NOT_BLANK)
    private String title;

    @Size(max = 4000)
    private String description;

    private Severity severity;

    @NotNull
    @Min(0)
    private Long version;

    private boolean descriptionSent;

    public void setCategoryId(UUID categoryId) {
      this.categoryId = categoryId;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    /** Sets or, with {@code null}, clears the description. */
    public void setDescription(String description) {
      this.description = description;
      this.descriptionSent = true;
    }

    public void setSeverity(Severity severity) {
      this.severity = severity;
    }

    public void setVersion(Long version) {
      this.version = version;
    }

    /** Version the client based the change on. */
    public Long version() {
      return version;
    }

    /** Applies the sent fields to the current ones. */
    public DefectFields applyTo(DefectFields current) {
      return new DefectFields(
          categoryId == null ? current.categoryId() : categoryId,
          title == null ? current.title() : title.trim(),
          descriptionSent ? trimToNull(description) : current.description(),
          severity == null ? current.severity() : severity);
    }
  }

  /** Observation of an existing defect. */
  public record CreateObservationRequest(
      @NotNull UUID defectId,
      @NotNull DefectStatus status,
      Severity severity,
      UUID checklistItemId,
      @Size(max = 2000) String comment) {

    /** Service input with trimmed text. */
    public NewObservation toNewObservation() {
      return new NewObservation(defectId, checklistItemId, status, severity, trimToNull(comment));
    }
  }

  /**
   * Partial observation update. Absent fields keep their values; an explicit JSON {@code null}
   * clears the comment, so its setter records whether it was sent.
   */
  public static final class UpdateObservationRequest {

    private DefectStatus status;

    private Severity severity;

    @Size(max = 2000)
    private String comment;

    @NotNull
    @Min(0)
    private Long version;

    private boolean commentSent;

    public void setStatus(DefectStatus status) {
      this.status = status;
    }

    public void setSeverity(Severity severity) {
      this.severity = severity;
    }

    /** Sets or, with {@code null}, clears the comment. */
    public void setComment(String comment) {
      this.comment = comment;
      this.commentSent = true;
    }

    public void setVersion(Long version) {
      this.version = version;
    }

    /** Version the client based the change on. */
    public Long version() {
      return version;
    }

    /** Applies the sent fields to the current ones. */
    public ObservationEntry applyTo(ObservationEntry current) {
      return new ObservationEntry(
          status == null ? current.status() : status,
          severity == null ? current.severity() : severity,
          commentSent ? trimToNull(comment) : current.comment());
    }
  }

  /** Defect without its observations. */
  public record DefectResponse(
      UUID id,
      UUID propertyId,
      UUID roomId,
      UUID categoryId,
      ElementGroup elementGroup,
      String title,
      String description,
      Severity severity,
      DefectStatus currentStatus,
      UUID firstInspectionId,
      Instant createdAt,
      Instant updatedAt,
      long version) {

    /** Maps an entity to the response. */
    public static DefectResponse of(Defect defect) {
      DefectFields fields = defect.fields();
      return new DefectResponse(
          defect.getId(),
          defect.getPropertyId(),
          defect.getRoomId(),
          fields.categoryId(),
          defect.getElementGroup(),
          fields.title(),
          fields.description(),
          fields.severity(),
          defect.getCurrentStatus(),
          defect.getFirstInspectionId(),
          defect.getCreatedAt(),
          defect.getUpdatedAt(),
          defect.getVersion());
    }
  }

  /** Defect with its observations, the latest inspection first. */
  public record DefectDetailsResponse(
      UUID id,
      UUID propertyId,
      UUID roomId,
      UUID categoryId,
      ElementGroup elementGroup,
      String title,
      String description,
      Severity severity,
      DefectStatus currentStatus,
      UUID firstInspectionId,
      Instant createdAt,
      Instant updatedAt,
      long version,
      List<ObservationResponse> observations) {

    /** Copies the observation list. */
    public DefectDetailsResponse {
      observations = List.copyOf(observations);
    }

    /** Maps a defect view to the response. */
    public static DefectDetailsResponse of(DefectView view) {
      DefectResponse base = DefectResponse.of(view.defect());
      return new DefectDetailsResponse(
          base.id(),
          base.propertyId(),
          base.roomId(),
          base.categoryId(),
          base.elementGroup(),
          base.title(),
          base.description(),
          base.severity(),
          base.currentStatus(),
          base.firstInspectionId(),
          base.createdAt(),
          base.updatedAt(),
          base.version(),
          view.observations().stream().map(ObservationResponse::of).toList());
    }
  }

  /** Page of defects. */
  public record DefectPage(List<DefectResponse> items, int page, int size, long total) {

    /** Copies the item list. */
    public DefectPage {
      items = List.copyOf(items);
    }

    /** Maps a Spring Data page. */
    public static DefectPage of(Page<Defect> page) {
      return new DefectPage(
          page.map(DefectResponse::of).getContent(),
          page.getNumber(),
          page.getSize(),
          page.getTotalElements());
    }
  }

  /** Photo attached to an observation. */
  public record ObservationPhotoResponse(UUID photoId, String caption, int sortOrder) {}

  /**
   * Observation as returned by the API. Photos are attached through media-service from T-206; until
   * then the list is always empty.
   */
  public record ObservationResponse(
      UUID id,
      UUID defectId,
      UUID inspectionId,
      UUID checklistItemId,
      DefectStatus status,
      Severity severity,
      String comment,
      UUID observedBy,
      List<ObservationPhotoResponse> photos,
      Instant createdAt,
      long version) {

    /** Copies the photo list. */
    public ObservationResponse {
      photos = List.copyOf(photos);
    }

    /** Maps an entity to the response; {@code null} stays {@code null}. */
    public static ObservationResponse of(DefectObservation observation) {
      if (observation == null) {
        return null;
      }
      ObservationEntry entry = observation.entry();
      return new ObservationResponse(
          observation.getId(),
          observation.getDefectId(),
          observation.getInspectionId(),
          observation.getChecklistItemId(),
          entry.status(),
          entry.severity(),
          entry.comment(),
          observation.getObservedBy(),
          List.of(),
          observation.getCreatedAt(),
          observation.getVersion());
    }
  }

  /** Observation in an inspection together with its defect. */
  public record ObservationWithDefectResponse(
      UUID id,
      UUID defectId,
      UUID inspectionId,
      UUID checklistItemId,
      DefectStatus status,
      Severity severity,
      String comment,
      UUID observedBy,
      List<ObservationPhotoResponse> photos,
      Instant createdAt,
      long version,
      DefectResponse defect) {

    /** Copies the photo list. */
    public ObservationWithDefectResponse {
      photos = List.copyOf(photos);
    }

    /** Maps an observed defect to the response. */
    public static ObservationWithDefectResponse of(ObservedDefect observed) {
      ObservationResponse base = ObservationResponse.of(observed.observation());
      return new ObservationWithDefectResponse(
          base.id(),
          base.defectId(),
          base.inspectionId(),
          base.checklistItemId(),
          base.status(),
          base.severity(),
          base.comment(),
          base.observedBy(),
          base.photos(),
          base.createdAt(),
          base.version(),
          DefectResponse.of(observed.defect()));
    }
  }

  /** Defect to re-check during a repeat inspection. */
  public record OpenDefectResponse(
      DefectResponse defect,
      ObservationResponse lastObservation,
      ObservationResponse currentObservation) {

    /** Maps an open defect to the response. */
    public static OpenDefectResponse of(OpenDefect open) {
      return new OpenDefectResponse(
          DefectResponse.of(open.defect()),
          ObservationResponse.of(open.lastObservation()),
          ObservationResponse.of(open.currentObservation()));
    }
  }

  /** Entry of the status history. */
  public record DefectStatusChangeResponse(
      DefectStatus fromStatus,
      DefectStatus toStatus,
      UUID inspectionId,
      UUID changedBy,
      Instant changedAt,
      String comment) {

    /** Maps an entity to the response. */
    public static DefectStatusChangeResponse of(DefectStatusChange change) {
      return new DefectStatusChangeResponse(
          change.getFromStatus(),
          change.getToStatus(),
          change.getInspectionId(),
          change.getChangedBy(),
          change.getChangedAt(),
          change.getComment());
    }
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
