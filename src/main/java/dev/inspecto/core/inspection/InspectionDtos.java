package dev.inspecto.core.inspection;

import dev.inspecto.core.dictionary.ElementGroup;
import dev.inspecto.core.inspection.ChecklistItem.ChecklistEntry;
import dev.inspecto.core.inspection.ChecklistRooms.ChecklistSelection;
import dev.inspecto.core.inspection.ChecklistRooms.RoomChecklist;
import dev.inspecto.core.inspection.InspectionService.InspectionView;
import dev.inspecto.core.inspection.InspectionService.NewInspection;
import dev.inspecto.core.property.PropertyDtos.RoomResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** Request and response bodies of the inspection and checklist API (platform/api/core.yaml). */
public final class InspectionDtos {

  private InspectionDtos() {}

  /** New inspection with the rooms and templates of its checklist. */
  public record CreateInspectionRequest(
      @NotNull Instant inspectedAt,
      @Size(max = 200) String title,
      @Size(max = 4000) String notes,
      @NotNull @Size(min = 1, max = 50) List<@NotNull UUID> roomIds,
      @NotNull @Size(min = 1, max = 20) List<@NotBlank String> templateCodes) {

    /** Copies the lists. */
    public CreateInspectionRequest {
      roomIds = roomIds == null ? null : List.copyOf(roomIds);
      templateCodes = templateCodes == null ? null : List.copyOf(templateCodes);
    }

    /** Service input with trimmed text. */
    public NewInspection toNewInspection() {
      InspectionFields fields =
          new InspectionFields(trimToNull(title), inspectedAt, trimToNull(notes));
      List<String> codes = templateCodes.stream().map(String::trim).toList();
      return new NewInspection(fields, new ChecklistSelection(roomIds, codes));
    }
  }

  /**
   * Partial update. Absent fields keep their values; an explicit JSON {@code null} clears {@code
   * title} or {@code notes}, so the setters record which of them were sent.
   */
  public static final class UpdateInspectionRequest {

    @Size(max = 200)
    private String title;

    private Instant inspectedAt;

    @Size(max = 4000)
    private String notes;

    /** Only {@code DRAFT → IN_PROGRESS} is allowed here; completion has its own endpoint. */
    @Pattern(regexp = "IN_PROGRESS")
    private String status;

    @NotNull
    @Min(0)
    private Long version;

    private boolean titleSent;
    private boolean notesSent;

    /** Sets or, with {@code null}, clears the title. */
    public void setTitle(String title) {
      this.title = title;
      this.titleSent = true;
    }

    public void setInspectedAt(Instant inspectedAt) {
      this.inspectedAt = inspectedAt;
    }

    /** Sets or, with {@code null}, clears the notes. */
    public void setNotes(String notes) {
      this.notes = notes;
      this.notesSent = true;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public void setVersion(Long version) {
      this.version = version;
    }

    /** Version the client based the change on. */
    public Long version() {
      return version;
    }

    /** Whether the client asked to start the inspection. */
    public boolean start() {
      return status != null;
    }

    /** Applies the sent fields to the current ones. */
    public InspectionFields applyTo(InspectionFields current) {
      return new InspectionFields(
          titleSent ? trimToNull(title) : current.title(),
          inspectedAt == null ? current.inspectedAt() : inspectedAt,
          notesSent ? trimToNull(notes) : current.notes());
    }
  }

  /** Request body that carries only the version, for state changes. */
  public record VersionOnly(@NotNull @Min(0) Long version) {}

  /** Inspection without its checklist. */
  public record InspectionResponse(
      UUID id,
      UUID propertyId,
      UUID inspectorId,
      String title,
      Instant inspectedAt,
      InspectionStatus status,
      String notes,
      Instant completedAt,
      Instant createdAt,
      Instant updatedAt,
      long version) {

    /** Maps an entity to the response. */
    public static InspectionResponse of(Inspection inspection) {
      InspectionFields fields = inspection.fields();
      return new InspectionResponse(
          inspection.getId(),
          inspection.getPropertyId(),
          inspection.getInspectorId(),
          fields.title(),
          fields.inspectedAt(),
          inspection.getStatus(),
          fields.notes(),
          inspection.getCompletedAt(),
          inspection.getCreatedAt(),
          inspection.getUpdatedAt(),
          inspection.getVersion());
    }
  }

  /**
   * Inspection in the history of a property. Defects are introduced in T-203; until then an
   * inspection has none, so the counters are zero.
   */
  public record InspectionSummaryResponse(
      UUID id,
      UUID propertyId,
      UUID inspectorId,
      String title,
      Instant inspectedAt,
      InspectionStatus status,
      String notes,
      Instant completedAt,
      Instant createdAt,
      Instant updatedAt,
      long version,
      int defectCount,
      int newDefectCount) {

    /** Maps an entity to the response. */
    public static InspectionSummaryResponse of(Inspection inspection) {
      InspectionResponse base = InspectionResponse.of(inspection);
      return new InspectionSummaryResponse(
          base.id(),
          base.propertyId(),
          base.inspectorId(),
          base.title(),
          base.inspectedAt(),
          base.status(),
          base.notes(),
          base.completedAt(),
          base.createdAt(),
          base.updatedAt(),
          base.version(),
          0,
          0);
    }
  }

  /** Page of inspections. */
  public record InspectionPage(
      List<InspectionSummaryResponse> items, int page, int size, long total) {

    /** Copies the item list. */
    public InspectionPage {
      items = List.copyOf(items);
    }

    /** Maps a Spring Data page. */
    public static InspectionPage of(Page<Inspection> page) {
      return new InspectionPage(
          page.map(InspectionSummaryResponse::of).getContent(),
          page.getNumber(),
          page.getSize(),
          page.getTotalElements());
    }
  }

  /** Inspection with its checklist grouped by room. */
  public record InspectionDetailsResponse(
      UUID id,
      UUID propertyId,
      UUID inspectorId,
      String title,
      Instant inspectedAt,
      InspectionStatus status,
      String notes,
      Instant completedAt,
      Instant createdAt,
      Instant updatedAt,
      long version,
      List<InspectionRoomResponse> rooms) {

    /** Copies the room list. */
    public InspectionDetailsResponse {
      rooms = List.copyOf(rooms);
    }

    /** Maps an inspection view to the response. */
    public static InspectionDetailsResponse of(InspectionView view) {
      InspectionResponse base = InspectionResponse.of(view.inspection());
      return new InspectionDetailsResponse(
          base.id(),
          base.propertyId(),
          base.inspectorId(),
          base.title(),
          base.inspectedAt(),
          base.status(),
          base.notes(),
          base.completedAt(),
          base.createdAt(),
          base.updatedAt(),
          base.version(),
          view.rooms().stream().map(InspectionRoomResponse::of).toList());
    }
  }

  /**
   * Room of an inspection with its checklist and observations. Observations are introduced in
   * T-203; until then the list is always empty.
   */
  public record InspectionRoomResponse(
      RoomResponse room, List<ChecklistItemResponse> checklist, List<Object> observations) {

    /** Copies the lists. */
    public InspectionRoomResponse {
      checklist = List.copyOf(checklist);
      observations = List.copyOf(observations);
    }

    /** Maps a room checklist to the response. */
    public static InspectionRoomResponse of(RoomChecklist roomChecklist) {
      return new InspectionRoomResponse(
          RoomResponse.of(roomChecklist.room()),
          roomChecklist.items().stream().map(ChecklistItemResponse::of).toList(),
          List.of());
    }
  }

  /** New custom checklist item. */
  public record CreateChecklistItemRequest(
      @NotNull UUID roomId,
      @NotNull ElementGroup elementGroup,
      @NotBlank @Size(max = 200) String title) {}

  /**
   * Partial item update. An absent {@code result} keeps its value; an explicit JSON {@code null}
   * clears the comment, so the setter records whether it was sent.
   */
  public static final class UpdateChecklistItemRequest {

    private ChecklistResult result;

    @Size(max = 2000)
    private String comment;

    @NotNull
    @Min(0)
    private Long version;

    private boolean commentSent;

    public void setResult(ChecklistResult result) {
      this.result = result;
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
    public ChecklistEntry applyTo(ChecklistEntry current) {
      return new ChecklistEntry(
          result == null ? current.result() : result,
          commentSent ? trimToNull(comment) : current.comment());
    }
  }

  /** Checklist item as returned by the API. */
  public record ChecklistItemResponse(
      UUID id,
      UUID inspectionId,
      UUID roomId,
      UUID templateItemId,
      ElementGroup elementGroup,
      String title,
      ChecklistResult result,
      String comment,
      boolean custom,
      int sortOrder,
      long version) {

    /** Maps an entity to the response. */
    public static ChecklistItemResponse of(ChecklistItem item) {
      ChecklistEntry entry = item.entry();
      return new ChecklistItemResponse(
          item.getId(),
          item.getInspectionId(),
          item.getRoomId(),
          item.getTemplateItemId(),
          item.getElementGroup(),
          item.getTitle(),
          entry.result(),
          entry.comment(),
          item.isCustom(),
          item.getSortOrder(),
          item.getVersion());
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
