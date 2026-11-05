package dev.inspecto.core.dictionary;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/**
 * Defect category: system (code, managed by ADMIN, used by ML) or own (visible to its owner only),
 * ADR-017. A deactivated category is not offered for new defects but stays on existing ones.
 */
@Entity
@Table(name = "defect_categories")
public class DefectCategory {

  @Id private UUID id;

  @Column(updatable = false)
  private String code;

  @Column(name = "owner_id", updatable = false)
  private UUID ownerId;

  @Convert(converter = LocalizedTextConverter.class)
  @Column(nullable = false, columnDefinition = "jsonb")
  @ColumnTransformer(write = "?::jsonb")
  private LocalizedText names;

  @Enumerated(EnumType.STRING)
  @Column(name = "element_group")
  private ElementGroup elementGroup;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected DefectCategory() {
    // for JPA
  }

  private DefectCategory(UUID id, String code, UUID ownerId, LocalizedText names, Instant now) {
    this.id = id;
    this.code = code;
    this.ownerId = ownerId;
    this.names = names;
    this.active = true;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** New system category. */
  public static DefectCategory system(
      UUID id, String code, LocalizedText names, ElementGroup group, Instant now) {
    DefectCategory category = new DefectCategory(id, code, null, names, now);
    category.elementGroup = group;
    return category;
  }

  /** New category of a user; its name is stored as the Russian text and shown in every locale. */
  public static DefectCategory own(
      UUID id, UUID ownerId, String name, ElementGroup group, Instant now) {
    DefectCategory category =
        new DefectCategory(id, null, ownerId, new LocalizedText(name, null), now);
    category.elementGroup = group;
    return category;
  }

  /** Applies the given changes; {@code null} keeps the current value. */
  public void update(
      LocalizedText newNames, ElementGroup newGroup, Boolean newActive, Instant now) {
    if (newNames != null) {
      names = newNames;
    }
    if (newGroup != null) {
      elementGroup = newGroup;
    }
    if (newActive != null) {
      active = newActive;
    }
    updatedAt = now;
  }

  public boolean isSystem() {
    return ownerId == null;
  }

  /** Whether the user may see the category: system categories are visible to everyone. */
  public boolean isVisibleTo(UUID userId) {
    return isSystem() || ownerId.equals(userId);
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public LocalizedText getNames() {
    return names;
  }

  public ElementGroup getElementGroup() {
    return elementGroup;
  }

  public boolean isActive() {
    return active;
  }
}
