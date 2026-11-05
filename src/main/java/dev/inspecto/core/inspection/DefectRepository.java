package dev.inspecto.core.inspection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence of defects. */
public interface DefectRepository
    extends JpaRepository<Defect, UUID>, JpaSpecificationExecutor<Defect> {

  /** Defect whose property belongs to the given owner. */
  @Query(
      """
      select d from Defect d
      where d.id = :id
        and exists (select 1 from Property p where p.id = d.propertyId and p.ownerId = :ownerId)
      """)
  Optional<Defect> findOwned(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

  List<Defect> findByPropertyIdOrderByCreatedAtAscIdAsc(UUID propertyId);

  long countByPropertyIdAndCurrentStatus(UUID propertyId, DefectStatus status);

  boolean existsByRoomId(UUID roomId);

  boolean existsByCategoryId(UUID categoryId);

  /** Number of defects first found in each of the given inspections. */
  @Query(
      """
      select d.firstInspectionId as inspectionId, count(d) as total from Defect d
      where d.firstInspectionId in :inspectionIds group by d.firstInspectionId
      """)
  List<InspectionCount> countFoundIn(@Param("inspectionIds") List<UUID> inspectionIds);

  /** Count of rows per inspection. */
  interface InspectionCount {
    UUID getInspectionId();

    long getTotal();
  }
}
