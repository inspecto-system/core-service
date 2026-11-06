package dev.inspecto.core.inspection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Photo of an observation (docs/DATABASE.md §3.5). The photo itself lives in media-service; core
 * keeps only its id, which belongs to one observation at most.
 */
@Entity
@Table(name = "observation_photos")
public class ObservationPhoto {

  @Id private UUID id;

  @Column(name = "observation_id", nullable = false, updatable = false)
  private UUID observationId;

  @Column(name = "photo_id", nullable = false, updatable = false)
  private UUID photoId;

  @Column(updatable = false)
  private String caption;

  @Column(name = "sort_order", nullable = false, updatable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ObservationPhoto() {
    // for JPA
  }

  /** Links a photo to an observation at the given position. */
  public ObservationPhoto(UUID id, UUID observationId, PhotoLink link, Instant now) {
    this.id = id;
    this.observationId = observationId;
    this.photoId = link.photoId();
    this.caption = link.caption();
    this.sortOrder = link.sortOrder();
    this.createdAt = now;
  }

  public UUID getObservationId() {
    return observationId;
  }

  public UUID getPhotoId() {
    return photoId;
  }

  public String getCaption() {
    return caption;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  /** Photo, optional caption and position of a new link. */
  public record PhotoLink(UUID photoId, String caption, int sortOrder) {}
}
