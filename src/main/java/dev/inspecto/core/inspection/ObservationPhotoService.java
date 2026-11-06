package dev.inspecto.core.inspection;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.common.error.FieldViolation;
import dev.inspecto.core.common.id.UuidV7;
import dev.inspecto.core.inspection.ObservationPhoto.PhotoLink;
import dev.inspecto.core.media.MediaClient;
import dev.inspecto.core.media.MediaClient.PhotoMetadata;
import dev.inspecto.core.media.MediaUnavailableException;
import dev.inspecto.core.outbox.OutboxEventType;
import dev.inspecto.core.outbox.OutboxService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Photos of observations (T-206). The photo is uploaded to media-service first; attaching it checks
 * with media-service who uploaded it and for which property, links it and marks it attached there.
 */
@Service
public class ObservationPhotoService {

  private final InspectionAccess access;
  private final ObservationPhotoRepository photos;
  private final MediaClient media;
  private final OutboxService outbox;
  private final Clock clock;

  /** Creates the service. */
  public ObservationPhotoService(
      InspectionAccess access,
      ObservationPhotoRepository photos,
      MediaClient media,
      OutboxService outbox,
      Clock clock) {
    this.access = access;
    this.photos = photos;
    this.media = media;
    this.outbox = outbox;
    this.clock = clock;
  }

  /**
   * Attaches a photo uploaded by the user for the property of the observation. Attaching the same
   * photo again returns the existing link, so clients may retry.
   *
   * @throws ApiException {@code VALIDATION_ERROR} for a photo of someone else or another property,
   *     {@code PHOTO_LIMIT_EXCEEDED}, {@code RESOURCE_IN_USE} for a photo of another observation,
   *     {@code INVALID_STATE_TRANSITION} for a completed inspection, {@code DEPENDENCY_UNAVAILABLE}
   */
  @Transactional
  public ObservationPhoto attach(UUID observationId, UUID userId, NewPhoto request) {
    DefectObservation observation = access.ownedObservation(observationId, userId);
    Inspection inspection = access.inspectionForShare(observation.getInspectionId());
    inspection.requireNotCompleted();
    Optional<ObservationPhoto> linked = photos.findByPhotoId(request.photoId());
    if (linked.isPresent()) {
      return sameObservation(linked.get(), observationId);
    }
    if (photos.countByObservationId(observationId) >= media.maxPhotosPerObservation()) {
      throw new ApiException(
          ErrorCode.PHOTO_LIMIT_EXCEEDED, "The observation has the maximum number of photos");
    }
    requireUploadedBy(request.photoId(), userId, inspection.getPropertyId());
    PhotoLink link =
        new PhotoLink(request.photoId(), request.caption(), photos.maxSortOrder(observationId) + 1);
    ObservationPhoto saved =
        save(new ObservationPhoto(UuidV7.generate(clock), observationId, link, clock.instant()));
    // Marked in media-service last: if this fails, the link is rolled back and the photo stays
    // unattached, to be cleaned up by media-service.
    if (!callMedia(() -> media.attach(request.photoId()))) {
      throw unknownPhoto();
    }
    return saved;
  }

  /**
   * Removes a photo from the observation; its files are deleted through the outbox (ADR-018).
   *
   * @throws ApiException {@code NOT_FOUND} when the observation has no such photo, {@code
   *     INVALID_STATE_TRANSITION} for a completed inspection
   */
  @Transactional
  public void detach(UUID observationId, UUID userId, UUID photoId) {
    DefectObservation observation = access.ownedObservation(observationId, userId);
    access.inspectionForShare(observation.getInspectionId()).requireNotCompleted();
    ObservationPhoto link =
        photos
            .findByPhotoId(photoId)
            .filter(p -> p.getObservationId().equals(observationId))
            .orElseThrow(() -> InspectionAccess.notFound("Photo not found"));
    photos.delete(link);
    outbox.enqueue(OutboxEventType.DELETE_PHOTO, Map.of("photoId", photoId.toString()));
  }

  private static ObservationPhoto sameObservation(ObservationPhoto link, UUID observationId) {
    if (!link.getObservationId().equals(observationId)) {
      throw new ApiException(
          ErrorCode.RESOURCE_IN_USE, "The photo is attached to another observation");
    }
    return link;
  }

  /** The photo must be one the user uploaded for this property (ids from media are not trusted). */
  private void requireUploadedBy(UUID photoId, UUID userId, UUID propertyId) {
    Optional<PhotoMetadata> metadata = callMedia(() -> media.metadata(photoId));
    boolean matches =
        metadata
            .filter(m -> userId.equals(m.ownerId()) && propertyId.equals(m.propertyId()))
            .isPresent();
    if (!matches) {
      throw unknownPhoto();
    }
  }

  private ObservationPhoto save(ObservationPhoto link) {
    try {
      return photos.saveAndFlush(link);
    } catch (DataIntegrityViolationException e) {
      // A concurrent request attached the same photo after the check above.
      throw new ApiException(
          ErrorCode.RESOURCE_IN_USE, "The photo is attached to another observation", e);
    }
  }

  private static <T> T callMedia(MediaCall<T> call) {
    try {
      return call.run();
    } catch (MediaUnavailableException e) {
      throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "media-service is unavailable", e);
    }
  }

  private static ApiException unknownPhoto() {
    return new ApiException(
        ErrorCode.VALIDATION_ERROR,
        "Request contains invalid fields",
        List.of(new FieldViolation("photoId", "UNKNOWN_ID")));
  }

  /** A call to media-service. */
  @FunctionalInterface
  private interface MediaCall<T> {
    T run();
  }

  /** Photo to attach with an optional caption. */
  public record NewPhoto(UUID photoId, String caption) {}
}
