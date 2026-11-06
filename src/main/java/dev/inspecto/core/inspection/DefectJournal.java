package dev.inspecto.core.inspection;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.common.id.UuidV7;
import dev.inspecto.core.inspection.DefectObservation.ObservationDraft;
import dev.inspecto.core.inspection.DefectObservation.ObservationEntry;
import dev.inspecto.core.inspection.DefectObservation.ObservationKey;
import dev.inspecto.core.inspection.DefectStatusChange.Cause;
import dev.inspecto.core.inspection.DefectStatusChange.Transition;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Observations of defects and the status history (ADR-003). The current status of a defect is the
 * status of its observation in the latest inspection; it is recomputed whenever an observation or
 * the date of an inspection changes, and every change is appended to the history.
 */
@Component
public class DefectJournal {

  private final DefectRepository defects;
  private final ObservationRepository observations;
  private final DefectHistoryRepository history;
  private final ObservationPhotoRepository photos;
  private final Clock clock;

  /** Creates the component. */
  public DefectJournal(
      DefectRepository defects,
      ObservationRepository observations,
      DefectHistoryRepository history,
      ObservationPhotoRepository photos,
      Clock clock) {
    this.defects = defects;
    this.observations = observations;
    this.history = history;
    this.photos = photos;
    this.clock = clock;
  }

  /** Records the first, {@code OPEN} observation of a new defect and starts its history. */
  public DefectObservation recordFirst(Defect defect, UUID userId, ObservationDraft draft) {
    UUID inspectionId = defect.getFirstInspectionId();
    DefectObservation observation =
        observations.save(
            new DefectObservation(
                UuidV7.generate(clock),
                new ObservationKey(defect.getId(), inspectionId, userId),
                draft,
                clock.instant()));
    Cause cause = new Cause(inspectionId, userId, clock.instant(), draft.entry().comment());
    append(defect.getId(), new Transition(null, defect.getCurrentStatus()), cause);
    return observation;
  }

  /**
   * Records an existing defect in an inspection and updates its current status.
   *
   * @throws ApiException {@code ALREADY_OBSERVED} when the defect already has an observation there
   */
  public DefectObservation record(Defect defect, ObservationKey key, ObservationDraft draft) {
    if (observations.existsByDefectIdAndInspectionId(defect.getId(), key.inspectionId())) {
      throw alreadyObserved(null);
    }
    DefectObservation observation;
    try {
      observation =
          observations.saveAndFlush(
              new DefectObservation(UuidV7.generate(clock), key, draft, clock.instant()));
    } catch (DataIntegrityViolationException e) {
      // A concurrent request observed the same defect after the check above.
      throw alreadyObserved(e);
    }
    refresh(defect, key.inspectionId(), key.observedBy(), draft.entry().comment());
    return observation;
  }

  /** Changes an observation and updates the current status of its defect. */
  public DefectObservation change(
      DefectObservation observation, ObservationEntry entry, UUID userId) {
    observation.apply(entry, clock.instant());
    DefectObservation saved = observations.saveAndFlush(observation);
    Defect defect = defects.getReferenceById(observation.getDefectId());
    refresh(defect, observation.getInspectionId(), userId, entry.comment());
    return saved;
  }

  /**
   * Recomputes the status of every defect observed in an inspection whose date changed, since the
   * order of inspections may have changed.
   */
  public void refreshObservedIn(UUID inspectionId, UUID userId) {
    for (DefectObservation observation :
        observations.findByInspectionIdOrderByCreatedAtAscIdAsc(inspectionId)) {
      Defect defect = defects.getReferenceById(observation.getDefectId());
      refresh(defect, inspectionId, userId, null);
    }
  }

  /** Observations of a defect with their photos, the latest inspection first. */
  public List<ObservationView> observationsOf(UUID defectId) {
    return withPhotos(observations.findLatestFirst(defectId));
  }

  /** The observations with their photos, loaded in one query; the order is kept. */
  public List<ObservationView> withPhotos(List<DefectObservation> list) {
    Map<UUID, List<ObservationPhoto>> byObservation =
        photos
            .findByObservationIdInOrderBySortOrderAscIdAsc(
                list.stream().map(DefectObservation::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(ObservationPhoto::getObservationId));
    return list.stream()
        .map(o -> new ObservationView(o, byObservation.getOrDefault(o.getId(), List.of())))
        .toList();
  }

  /** Status history of a defect, oldest first. */
  public List<DefectStatusChange> historyOf(UUID defectId) {
    return history.findByDefectIdOrderByChangedAtAscIdAsc(defectId);
  }

  /** Sets the status of the latest observation as the current status of the defect. */
  private void refresh(Defect defect, UUID inspectionId, UUID userId, String comment) {
    DefectStatus latest = observations.findLatestFirst(defect.getId()).getFirst().getStatus();
    DefectStatus current = defect.getCurrentStatus();
    if (latest == current) {
      return;
    }
    Instant now = clock.instant();
    defect.changeStatus(latest, now);
    defects.saveAndFlush(defect);
    append(
        defect.getId(),
        new Transition(current, latest),
        new Cause(inspectionId, userId, now, comment));
  }

  private void append(UUID defectId, Transition transition, Cause cause) {
    history.save(new DefectStatusChange(UuidV7.generate(clock), defectId, transition, cause));
  }

  private static ApiException alreadyObserved(Throwable cause) {
    String detail = "The defect is already observed in this inspection";
    return cause == null
        ? new ApiException(ErrorCode.ALREADY_OBSERVED, detail)
        : new ApiException(ErrorCode.ALREADY_OBSERVED, detail, cause);
  }
}
