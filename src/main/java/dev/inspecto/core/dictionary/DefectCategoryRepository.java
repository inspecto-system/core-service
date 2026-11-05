package dev.inspecto.core.dictionary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence of defect categories. */
public interface DefectCategoryRepository extends JpaRepository<DefectCategory, UUID> {

  /** System categories and the user's own ones. */
  @Query("select c from DefectCategory c where c.ownerId is null or c.ownerId = :userId")
  List<DefectCategory> findVisible(@Param("userId") UUID userId);

  Optional<DefectCategory> findByIdAndOwnerId(UUID id, UUID ownerId);

  Optional<DefectCategory> findByIdAndOwnerIdIsNull(UUID id);

  boolean existsByCodeAndOwnerIdIsNull(String code);
}
