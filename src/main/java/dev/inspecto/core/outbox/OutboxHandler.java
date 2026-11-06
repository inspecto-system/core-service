package dev.inspecto.core.outbox;

import dev.inspecto.core.media.MediaClient;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Performs one outbox job by calling the service that owns the files (ADR-018). */
@Component
public class OutboxHandler {

  private final MediaClient media;
  private final JsonMapper json;

  /** Creates the handler. */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring injects shared singleton beans; copying them is meaningless")
  public OutboxHandler(MediaClient media, JsonMapper json) {
    this.media = media;
    this.json = json;
  }

  /**
   * Runs the job; every job is idempotent, so a retry after a partial failure is safe.
   *
   * @throws dev.inspecto.core.media.MediaUnavailableException when media-service cannot do it now
   * @throws IllegalArgumentException when the payload is malformed
   */
  public void handle(OutboxEvent event) {
    JsonNode payload = json.readTree(event.getPayload());
    // An exhaustive switch expression: a new event type does not compile until it is handled here.
    Runnable job =
        switch (event.getType()) {
          // Reports of a property or an account are deleted here as well once report-service exists
          // (T-401, T-402).
          case DELETE_PROPERTY_FILES -> () -> media.deletePropertyPhotos(id(payload, "propertyId"));
          case DELETE_USER_FILES -> () -> media.deleteOwnerPhotos(id(payload, "ownerId"));
          case DELETE_PHOTO -> () -> media.deletePhoto(id(payload, "photoId"));
        };
    job.run();
  }

  private static UUID id(JsonNode payload, String field) {
    JsonNode value = payload.get(field);
    if (value == null || !value.isString()) {
      throw new IllegalArgumentException("Outbox payload has no " + field);
    }
    return UUID.fromString(value.asString());
  }
}
