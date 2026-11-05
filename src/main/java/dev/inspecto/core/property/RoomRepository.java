package dev.inspecto.core.property;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence of rooms. */
public interface RoomRepository extends JpaRepository<Room, UUID> {

  List<Room> findByPropertyIdOrderBySortOrderAscIdAsc(UUID propertyId);

  /** Room whose property belongs to the given owner. */
  @Query(
      """
      select r from Room r
      where r.id = :roomId
        and exists (select 1 from Property p where p.id = r.propertyId and p.ownerId = :ownerId)
      """)
  Optional<Room> findOwned(@Param("roomId") UUID roomId, @Param("ownerId") UUID ownerId);

  /** Largest sort order among the rooms of a property, or {@code -1} when it has none. */
  @Query("select coalesce(max(r.sortOrder), -1) from Room r where r.propertyId = :propertyId")
  int maxSortOrder(@Param("propertyId") UUID propertyId);
}
