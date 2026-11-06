package dev.inspecto.core.inspection;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.OptimisticVersion;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Changing, starting, completing and deleting inspections (T-201). */
@Service
public class InspectionLifecycleService {

  private final InspectionAccess access;
  private final InspectionRepository inspections;
  private final DraftDeletion draftDeletion;
  private final DefectJournal journal;
  private final Clock clock;

  /** Creates the service. */
  public InspectionLifecycleService(
      InspectionAccess access,
      InspectionRepository inspections,
      DraftDeletion draftDeletion,
      DefectJournal journal,
      Clock clock) {
    this.access = access;
    this.inspections = inspections;
    this.draftDeletion = draftDeletion;
    this.journal = journal;
    this.clock = clock;
  }

  /**
   * Changes the descriptive fields and optionally starts the inspection. A new date can change the
   * order of inspections, so the status of the defects observed here is recomputed.
   *
   * @throws ApiException {@code VERSION_CONFLICT} for a stale version, {@code
   *     INVALID_STATE_TRANSITION} for a completed inspection
   */
  @Transactional
  public Inspection update(UUID inspectionId, UUID userId, InspectionUpdate update) {
    Inspection inspection = access.ownedInspection(inspectionId, userId);
    OptimisticVersion.require(inspection.getVersion(), update.expectedVersion());
    Instant previousDate = inspection.fields().inspectedAt();
    inspection.update(update.fields().apply(inspection.fields()), update.start(), clock.instant());
    Inspection saved = inspections.saveAndFlush(inspection);
    if (!previousDate.equals(saved.fields().inspectedAt())) {
      journal.refreshObservedIn(inspectionId, userId);
    }
    return saved;
  }

  /**
   * Completes an inspection in progress.
   *
   * @throws ApiException {@code VERSION_CONFLICT} for a stale version, {@code
   *     INVALID_STATE_TRANSITION} unless the inspection is in progress
   */
  @Transactional
  public Inspection complete(UUID inspectionId, UUID userId, long expectedVersion) {
    Inspection inspection = access.ownedInspection(inspectionId, userId);
    OptimisticVersion.require(inspection.getVersion(), expectedVersion);
    inspection.complete(clock.instant());
    return inspections.saveAndFlush(inspection);
  }

  /**
   * Deletes a draft inspection with its checklist and the defects first found in it; their photos
   * are deleted in media-service through the outbox.
   *
   * @throws ApiException {@code INVALID_STATE_TRANSITION} unless the inspection is a draft, {@code
   *     RESOURCE_IN_USE} when deleting it would change the defects of other inspections
   */
  @Transactional
  public void delete(UUID inspectionId, UUID userId) {
    Inspection inspection = access.ownedInspection(inspectionId, userId);
    inspection.requireDeletable();
    draftDeletion.prepare(inspectionId);
    inspections.delete(inspection);
  }

  /**
   * Requested change of an inspection.
   *
   * @param fields computes the new descriptive fields from the current ones
   * @param start whether to start a draft
   * @param expectedVersion version the client based the change on
   */
  public record InspectionUpdate(
      UnaryOperator<InspectionFields> fields, boolean start, long expectedVersion) {}
}
