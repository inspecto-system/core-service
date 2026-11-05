package dev.inspecto.core.inspection;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence of inspections. */
public interface InspectionRepository extends JpaRepository<Inspection, UUID> {

  /** Inspection whose property belongs to the given owner. */
  @Query(
      """
      select i from Inspection i
      where i.id = :id
        and exists (select 1 from Property p where p.id = i.propertyId and p.ownerId = :ownerId)
      """)
  Optional<Inspection> findOwned(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

  /**
   * Same as {@link #findOwned} but takes a shared row lock ({@code FOR SHARE}) until the end of the
   * transaction, so the inspection cannot be completed while its checklist is being changed.
   */
  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query(
      """
      select i from Inspection i
      where i.id = :id
        and exists (select 1 from Property p where p.id = i.propertyId and p.ownerId = :ownerId)
      """)
  Optional<Inspection> findOwnedForShare(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

  /** Inspection locked with {@code FOR SHARE}; access must already be checked. */
  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query("select i from Inspection i where i.id = :id")
  Optional<Inspection> findForShare(@Param("id") UUID id);

  Page<Inspection> findByPropertyId(UUID propertyId, Pageable pageable);

  long countByPropertyId(UUID propertyId);

  /** Latest inspection date of a property, or {@code null} without inspections. */
  @Query("select max(i.inspectedAt) from Inspection i where i.propertyId = :propertyId")
  Instant lastInspectedAt(@Param("propertyId") UUID propertyId);
}
