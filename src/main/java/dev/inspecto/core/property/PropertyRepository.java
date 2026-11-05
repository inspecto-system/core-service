package dev.inspecto.core.property;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence of properties; every lookup is scoped to the owner. */
public interface PropertyRepository extends JpaRepository<Property, UUID> {

  Optional<Property> findByIdAndOwnerId(UUID id, UUID ownerId);

  /**
   * Own properties filtered by archive state and a case-insensitive {@code LIKE} pattern applied to
   * the name and the address; {@code \} escapes wildcards in the pattern.
   */
  @Query(
      """
      select p from Property p
      where p.ownerId = :ownerId
        and ((:archived = true and p.archivedAt is not null)
          or (:archived = false and p.archivedAt is null))
        and (lower(p.name) like lower(:pattern) escape '\\'
          or lower(p.address) like lower(:pattern) escape '\\')
      """)
  Page<Property> search(
      @Param("ownerId") UUID ownerId,
      @Param("archived") boolean archived,
      @Param("pattern") String pattern,
      Pageable pageable);
}
