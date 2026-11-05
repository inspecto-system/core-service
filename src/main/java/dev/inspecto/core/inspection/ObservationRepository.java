package dev.inspecto.core.inspection;

import dev.inspecto.core.inspection.DefectRepository.InspectionCount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence of defect observations. */
public interface ObservationRepository extends JpaRepository<DefectObservation, UUID> {

  /** Observation whose inspection belongs to a property of the given owner. */
  @Query(
      """
      select o from DefectObservation o
      where o.id = :id
        and exists (
          select 1 from Inspection i, Property p
          where i.id = o.inspectionId and p.id = i.propertyId and p.ownerId = :ownerId)
      """)
  Optional<DefectObservation> findOwned(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

  /**
   * Observations of a defect, the latest inspection first: by inspection date, then by when the
   * inspection was created. The first one determines the current status of the defect.
   */
  @Query(
      """
      select o from DefectObservation o, Inspection i
      where i.id = o.inspectionId and o.defectId = :defectId
      order by i.inspectedAt desc, i.createdAt desc, i.id desc
      """)
  List<DefectObservation> findLatestFirst(@Param("defectId") UUID defectId);

  List<DefectObservation> findByInspectionIdOrderByCreatedAtAscIdAsc(UUID inspectionId);

  /** Observations of all defects of a property. */
  @Query(
      """
      select o from DefectObservation o, Defect d
      where d.id = o.defectId and d.propertyId = :propertyId
      """)
  List<DefectObservation> findByPropertyId(@Param("propertyId") UUID propertyId);

  boolean existsByDefectIdAndInspectionId(UUID defectId, UUID inspectionId);

  /**
   * Whether deleting the draft would affect other inspections: the draft observes a defect found
   * elsewhere, or a defect found in the draft is observed elsewhere.
   */
  @Query(
      """
      select count(o) > 0 from DefectObservation o, Defect d
      where d.id = o.defectId
        and ((o.inspectionId = :inspectionId and d.firstInspectionId <> :inspectionId)
          or (o.inspectionId <> :inspectionId and d.firstInspectionId = :inspectionId))
      """)
  boolean linksOtherInspections(@Param("inspectionId") UUID inspectionId);

  /** Number of {@code OPEN} observations in each of the given inspections. */
  @Query(
      """
      select o.inspectionId as inspectionId, count(o) as total from DefectObservation o
      where o.inspectionId in :inspectionIds
        and o.status = dev.inspecto.core.inspection.DefectStatus.OPEN
      group by o.inspectionId
      """)
  List<InspectionCount> countOpenIn(@Param("inspectionIds") List<UUID> inspectionIds);
}
