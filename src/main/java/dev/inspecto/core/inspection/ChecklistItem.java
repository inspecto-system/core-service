package dev.inspecto.core.inspection;

import dev.inspecto.core.dictionary.ElementGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Checklist item of an inspection room (docs/DATABASE.md §3.4). The title is copied from the
 * template when the inspection is created, so later template changes do not alter past inspections.
 */
@Entity
@Table(name = "inspection_checklist_items")
public class ChecklistItem {

  @Id private UUID id;

  @Column(name = "inspection_id", nullable = false, updatable = false)
  private UUID inspectionId;

  @Column(name = "room_id", nullable = false, updatable = false)
  private UUID roomId;

  @Column(name = "template_item_id", updatable = false)
  private UUID templateItemId;

  @Enumerated(EnumType.STRING)
  @Column(name = "element_group", nullable = false, updatable = false)
  private ElementGroup elementGroup;

  @Column(nullable = false, updatable = false)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ChecklistResult result;

  private String comment;

  @Column(name = "sort_order", nullable = false, updatable = false)
  private int sortOrder;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected ChecklistItem() {
    // for JPA
  }

  /** Creates an unchecked item of the given inspection. */
  public ChecklistItem(UUID id, UUID inspectionId, ChecklistItemDraft draft, Instant now) {
    this.id = id;
    this.inspectionId = inspectionId;
    this.roomId = draft.roomId();
    this.templateItemId = draft.templateItemId();
    this.elementGroup = draft.elementGroup();
    this.title = draft.title();
    this.sortOrder = draft.sortOrder();
    this.result = ChecklistResult.NOT_CHECKED;
    this.updatedAt = now;
  }

  /** Records the result of the check. */
  public void record(ChecklistEntry entry, Instant now) {
    this.result = entry.result();
    this.comment = entry.comment();
    this.updatedAt = now;
  }

  /** Current result and comment. */
  public ChecklistEntry entry() {
    return new ChecklistEntry(result, comment);
  }

  /** Whether the inspector added the item rather than taking it from a template. */
  public boolean isCustom() {
    return templateItemId == null;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public UUID getRoomId() {
    return roomId;
  }

  public UUID getTemplateItemId() {
    return templateItemId;
  }

  public ElementGroup getElementGroup() {
    return elementGroup;
  }

  public String getTitle() {
    return title;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public long getVersion() {
    return version;
  }

  /**
   * Fields of a new item.
   *
   * @param templateItemId template item it was copied from; {@code null} for a custom item
   */
  public record ChecklistItemDraft(
      UUID roomId, UUID templateItemId, ElementGroup elementGroup, String title, int sortOrder) {}

  /** Result of a check with an optional comment. */
  public record ChecklistEntry(ChecklistResult result, String comment) {}
}
