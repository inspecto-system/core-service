package dev.inspecto.core.inspection;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.OptimisticVersion;
import dev.inspecto.core.common.id.UuidV7;
import dev.inspecto.core.dictionary.ElementGroup;
import dev.inspecto.core.dictionary.Severity;
import dev.inspecto.core.inspection.Defect.DefectFields;
import dev.inspecto.core.inspection.Defect.DefectOrigin;
import dev.inspecto.core.inspection.DefectObservation.ObservationDraft;
import dev.inspecto.core.inspection.DefectObservation.ObservationEntry;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Defects of the user's properties (T-203, ADR-003). */
@Service
public class DefectService {

  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

  private final InspectionAccess access;
  private final DefectReferences references;
  private final DefectRepository defects;
  private final DefectJournal journal;
  private final Clock clock;

  /** Creates the service. */
  public DefectService(
      InspectionAccess access,
      DefectReferences references,
      DefectRepository defects,
      DefectJournal journal,
      Clock clock) {
    this.access = access;
    this.references = references;
    this.defects = defects;
    this.journal = journal;
    this.clock = clock;
  }

  /** Defects of an own property, newest first, optionally filtered. */
  @Transactional(readOnly = true)
  public Page<Defect> list(UUID propertyId, UUID userId, DefectFilter filter, int page, int size) {
    access.requireOwnedProperty(propertyId, userId);
    return defects.findAll(
        filter.toSpecification(propertyId), PageRequest.of(page, size, NEWEST_FIRST));
  }

  /**
   * Registers a defect found in the inspection together with its first, {@code OPEN} observation.
   *
   * @throws ApiException {@code VALIDATION_ERROR} for an unknown room, category or checklist item,
   *     {@code INVALID_STATE_TRANSITION} when the inspection is completed
   */
  @Transactional
  public DefectView create(UUID inspectionId, UUID userId, NewDefect request) {
    Inspection inspection = access.ownedInspectionForShare(inspectionId, userId);
    inspection.requireNotCompleted();
    UUID propertyId = inspection.getPropertyId();
    references.requireRoom(propertyId, request.roomId());
    references.requireUsableCategory(request.fields().categoryId(), userId);
    references.requireChecklistItem(request.checklistItemId(), inspectionId, request.roomId());
    DefectOrigin origin =
        new DefectOrigin(
            propertyId, request.roomId(), request.elementGroup(), inspectionId, userId);
    Defect defect =
        defects.save(new Defect(UuidV7.generate(clock), origin, request.fields(), clock.instant()));
    ObservationEntry entry =
        new ObservationEntry(DefectStatus.OPEN, request.fields().severity(), request.comment());
    DefectObservation first =
        journal.recordFirst(defect, userId, new ObservationDraft(request.checklistItemId(), entry));
    return new DefectView(defect, List.of(first));
  }

  /** Defect with its observations, the latest inspection first. */
  @Transactional(readOnly = true)
  public DefectView get(UUID defectId, UUID userId) {
    Defect defect = access.ownedDefect(defectId, userId);
    return new DefectView(defect, journal.observationsOf(defectId));
  }

  /**
   * Changes the category, title, description or severity.
   *
   * @throws ApiException {@code VERSION_CONFLICT} for a stale version, {@code VALIDATION_ERROR} for
   *     an unusable category
   */
  @Transactional
  public Defect update(UUID defectId, UUID userId, DefectUpdate update) {
    Defect defect = access.ownedDefect(defectId, userId);
    OptimisticVersion.require(defect.getVersion(), update.expectedVersion());
    DefectFields fields = update.fields().apply(defect.fields());
    if (!fields.categoryId().equals(defect.fields().categoryId())) {
      references.requireUsableCategory(fields.categoryId(), userId);
    }
    defect.apply(fields, clock.instant());
    return defects.saveAndFlush(defect);
  }

  /** Status history of a defect, oldest first. */
  @Transactional(readOnly = true)
  public List<DefectStatusChange> history(UUID defectId, UUID userId) {
    access.ownedDefect(defectId, userId);
    return journal.historyOf(defectId);
  }

  /**
   * New defect.
   *
   * @param checklistItemId optional item of the inspection the defect was found at
   * @param comment optional comment of the first observation
   */
  public record NewDefect(
      UUID roomId,
      UUID checklistItemId,
      ElementGroup elementGroup,
      DefectFields fields,
      String comment) {}

  /**
   * Requested change of a defect.
   *
   * @param fields computes the new fields from the current ones
   */
  public record DefectUpdate(UnaryOperator<DefectFields> fields, long expectedVersion) {}

  /** Optional filters of the defect list; {@code null} means "any". */
  public record DefectFilter(DefectStatus status, UUID roomId, Severity severity) {

    Specification<Defect> toSpecification(UUID propertyId) {
      Specification<Defect> spec =
          (root, query, cb) -> cb.equal(root.get("propertyId"), propertyId);
      if (status != null) {
        spec = spec.and((root, query, cb) -> cb.equal(root.get("currentStatus"), status));
      }
      if (roomId != null) {
        spec = spec.and((root, query, cb) -> cb.equal(root.get("roomId"), roomId));
      }
      if (severity != null) {
        spec = spec.and((root, query, cb) -> cb.equal(root.get("severity"), severity));
      }
      return spec;
    }
  }

  /** Defect with its observations, the latest inspection first. */
  public record DefectView(Defect defect, List<DefectObservation> observations) {

    /** Copies the observation list. */
    public DefectView {
      observations = List.copyOf(observations);
    }
  }
}
