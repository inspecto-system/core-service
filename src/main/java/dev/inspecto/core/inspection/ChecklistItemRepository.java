package dev.inspecto.core.inspection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence of inspection checklist items. */
public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, UUID> {

  List<ChecklistItem> findByInspectionIdOrderBySortOrderAscIdAsc(UUID inspectionId);

  /** Item whose inspection belongs to a property of the given owner. */
  @Query(
      """
      select c from ChecklistItem c
      where c.id = :id
        and exists (
          select 1 from Inspection i, Property p
          where i.id = c.inspectionId and p.id = i.propertyId and p.ownerId = :ownerId)
      """)
  Optional<ChecklistItem> findOwned(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

  boolean existsByRoomId(UUID roomId);

  /** Largest sort order of a room's items in an inspection, or {@code -1} when there are none. */
  @Query(
      """
      select coalesce(max(c.sortOrder), -1) from ChecklistItem c
      where c.inspectionId = :inspectionId and c.roomId = :roomId
      """)
  int maxSortOrder(@Param("inspectionId") UUID inspectionId, @Param("roomId") UUID roomId);
}
