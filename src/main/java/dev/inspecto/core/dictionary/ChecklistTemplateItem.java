package dev.inspecto.core.dictionary;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Item of a checklist template. */
@Entity
@Table(name = "checklist_template_items")
public class ChecklistTemplateItem {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "element_group", nullable = false)
  private ElementGroup elementGroup;

  @Convert(converter = LocalizedTextConverter.class)
  @Column(nullable = false, columnDefinition = "jsonb")
  private LocalizedText titles;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected ChecklistTemplateItem() {
    // for JPA
  }

  public UUID getId() {
    return id;
  }

  public ElementGroup getElementGroup() {
    return elementGroup;
  }

  public LocalizedText getTitles() {
    return titles;
  }

  public int getSortOrder() {
    return sortOrder;
  }
}
