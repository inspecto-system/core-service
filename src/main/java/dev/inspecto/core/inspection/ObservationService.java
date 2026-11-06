package dev.inspecto.core.inspection;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.OptimisticVersion;
import dev.inspecto.core.dictionary.Severity;
import dev.inspecto.core.inspection.DefectObservation.ObservationDraft;
import dev.inspecto.core.inspection.DefectObservation.ObservationEntry;
import dev.inspecto.core.inspection.DefectObservation.ObservationKey;
import dev.inspecto.core.inspection.OpenDefects.OpenDefect;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Observations of existing defects during repeat inspections (T-203). Changes lock the inspection
 * with {@code FOR SHARE}, as checklist changes do, so they cannot slip in after completion.
 */
@Service
public class ObservationService {

  private final InspectionAccess access;
  private final DefectReferences references;
  private final DefectJournal journal;
  private final OpenDefects openDefects;

  /** Creates the service. */
  public ObservationService(
      InspectionAccess access,
      DefectReferences references,
      DefectJournal journal,
      OpenDefects openDefects) {
    this.access = access;
    this.references = references;
    this.journal = journal;
    this.openDefects = openDefects;
  }

  /**
   * Records an existing defect of the property in the inspection.
   *
   * @throws ApiException {@code VALIDATION_ERROR} for an unknown defect or checklist item, {@code
   *     ALREADY_OBSERVED} for a second observation, {@code INVALID_STATE_TRANSITION} when the
   *     inspection is completed
   */
  @Transactional
  public ObservationView create(UUID inspectionId, UUID userId, NewObservation request) {
    Inspection inspection = access.ownedInspectionForShare(inspectionId, userId);
    inspection.requireNotCompleted();
    Defect defect = references.defectOf(inspection.getPropertyId(), request.defectId());
    references.requireChecklistItem(request.checklistItemId(), inspectionId, defect.getRoomId());
    Severity severity =
        request.severity() == null ? defect.fields().severity() : request.severity();
    ObservationEntry entry = new ObservationEntry(request.status(), severity, request.comment());
    return ObservationView.withoutPhotos(
        journal.record(
            defect,
            new ObservationKey(defect.getId(), inspectionId, userId),
            new ObservationDraft(request.checklistItemId(), entry)));
  }

  /**
   * Changes the status, severity or comment of an observation.
   *
   * @throws ApiException {@code VERSION_CONFLICT} for a stale version, {@code
   *     INVALID_STATE_TRANSITION} when the inspection is completed
   */
  @Transactional
  public ObservationView update(UUID observationId, UUID userId, ObservationUpdate update) {
    DefectObservation observation = access.ownedObservation(observationId, userId);
    access.inspectionForShare(observation.getInspectionId()).requireNotCompleted();
    OptimisticVersion.require(observation.getVersion(), update.expectedVersion());
    DefectObservation changed =
        journal.change(observation, update.entry().apply(observation.entry()), userId);
    return journal.withPhotos(List.of(changed)).getFirst();
  }

  /** Defects open before the inspection, with their observation in it, if any. */
  @Transactional(readOnly = true)
  public List<OpenDefect> openDefects(UUID inspectionId, UUID userId) {
    return openDefects.before(access.ownedInspection(inspectionId, userId));
  }

  /**
   * Observation of an existing defect.
   *
   * @param severity {@code null} keeps the current severity of the defect
   */
  public record NewObservation(
      UUID defectId,
      UUID checklistItemId,
      DefectStatus status,
      Severity severity,
      String comment) {}

  /**
   * Requested change of an observation.
   *
   * @param entry computes the new status, severity and comment from the current ones
   */
  public record ObservationUpdate(UnaryOperator<ObservationEntry> entry, long expectedVersion) {}
}
