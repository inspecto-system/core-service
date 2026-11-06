package dev.inspecto.core.inspection;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.outbox.OutboxEventType;
import dev.inspecto.core.outbox.OutboxService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Checks and side effects of deleting a draft inspection. */
@Component
public class DraftDeletion {

  private final ObservationRepository observations;
  private final ObservationPhotoRepository photos;
  private final OutboxService outbox;

  /** Creates the component. */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring injects shared singleton beans; copying them is meaningless")
  public DraftDeletion(
      ObservationRepository observations, ObservationPhotoRepository photos, OutboxService outbox) {
    this.observations = observations;
    this.photos = photos;
    this.outbox = outbox;
  }

  /**
   * Prepares the deletion of a draft in the caller's transaction: refuses a draft that shares
   * defects with other inspections and schedules the deletion of its photos in media-service.
   *
   * @throws ApiException {@code RESOURCE_IN_USE} when deleting the draft would change the defects
   *     of other inspections
   */
  public void prepare(UUID inspectionId) {
    if (observations.linksOtherInspections(inspectionId)) {
      throw new ApiException(
          ErrorCode.RESOURCE_IN_USE, "The draft shares defects with other inspections");
    }
    for (UUID photoId : photos.photoIdsOfInspection(inspectionId)) {
      outbox.enqueue(OutboxEventType.DELETE_PHOTO, Map.of("photoId", photoId.toString()));
    }
  }
}
