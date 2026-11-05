package dev.inspecto.core.inspection;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.id.UuidV7;
import dev.inspecto.core.dictionary.AppLocale;
import dev.inspecto.core.inspection.ChecklistRooms.ChecklistSelection;
import dev.inspecto.core.inspection.InspectionViews.InspectionSummary;
import dev.inspecto.core.inspection.InspectionViews.InspectionView;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating and reading inspections of the user's properties (T-201). */
@Service
public class InspectionService {

  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc("inspectedAt"), Sort.Order.desc("id"));

  private final InspectionAccess access;
  private final InspectionRepository inspections;
  private final ChecklistRooms checklistRooms;
  private final InspectionViews views;
  private final Clock clock;

  /** Creates the service. */
  public InspectionService(
      InspectionAccess access,
      InspectionRepository inspections,
      ChecklistRooms checklistRooms,
      InspectionViews views,
      Clock clock) {
    this.access = access;
    this.inspections = inspections;
    this.checklistRooms = checklistRooms;
    this.views = views;
    this.clock = clock;
  }

  /** Inspections of an own property with defect counters, the latest inspection date first. */
  @Transactional(readOnly = true)
  public Page<InspectionSummary> list(UUID propertyId, UUID userId, int page, int size) {
    access.requireOwnedProperty(propertyId, userId);
    Page<Inspection> found =
        inspections.findByPropertyId(propertyId, PageRequest.of(page, size, NEWEST_FIRST));
    List<InspectionSummary> summaries = views.summaries(found.getContent());
    return new PageImpl<>(summaries, found.getPageable(), found.getTotalElements());
  }

  /**
   * Creates a draft inspection with its checklist in one transaction.
   *
   * @throws ApiException {@code VALIDATION_ERROR} when a room or a template is unknown
   */
  @Transactional
  public InspectionView create(
      UUID propertyId, UUID userId, NewInspection request, AppLocale locale) {
    access.requireOwnedProperty(propertyId, userId);
    Inspection inspection =
        inspections.save(
            new Inspection(
                UuidV7.generate(clock), propertyId, userId, request.fields(), clock.instant()));
    List<ChecklistItem> checklist =
        checklistRooms.createChecklist(inspection, userId, request.selection(), locale);
    return views.of(inspection, checklist);
  }

  /** Inspection with its checklist and observations grouped by room. */
  @Transactional(readOnly = true)
  public InspectionView get(UUID inspectionId, UUID userId) {
    return views.of(access.ownedInspection(inspectionId, userId));
  }

  /** Fields of a new inspection with the rooms and templates of its checklist. */
  public record NewInspection(InspectionFields fields, ChecklistSelection selection) {}
}
