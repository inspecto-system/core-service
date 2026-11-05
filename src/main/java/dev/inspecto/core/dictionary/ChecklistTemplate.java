package dev.inspecto.core.dictionary;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;

/**
 * Checklist template: items are copied into an inspection for every selected room (T-201). System
 * templates are created by migrations; own templates are part of the extension stage.
 */
@Entity
@Table(name = "checklist_templates")
public class ChecklistTemplate {

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private String code;

  @Column(name = "owner_id", updatable = false)
  private UUID ownerId;

  @Convert(converter = LocalizedTextConverter.class)
  @Column(nullable = false, columnDefinition = "jsonb")
  private LocalizedText names;

  @OneToMany
  @JoinColumn(name = "template_id")
  @OrderBy("sortOrder")
  private List<ChecklistTemplateItem> items;

  protected ChecklistTemplate() {
    // for JPA
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  /** Owner of an own template; {@code null} for system templates. */
  public UUID getOwnerId() {
    return ownerId;
  }

  public LocalizedText getNames() {
    return names;
  }

  /** Items in display order. */
  public List<ChecklistTemplateItem> getItems() {
    return List.copyOf(items);
  }
}
