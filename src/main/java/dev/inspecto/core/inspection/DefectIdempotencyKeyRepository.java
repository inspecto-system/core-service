package dev.inspecto.core.inspection;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Idempotency keys of created defects. */
public interface DefectIdempotencyKeyRepository extends JpaRepository<DefectIdempotencyKey, UUID> {

  Optional<DefectIdempotencyKey> findByUserIdAndKey(UUID userId, String key);

  /**
   * Waits until no other transaction handles the same key of the user, so that two concurrent
   * repetitions cannot both create a defect; the lock ends with the transaction.
   */
  @Query(
      value = "select 1 from pg_advisory_xact_lock(hashtextextended(:lockKey, 0))",
      nativeQuery = true)
  Integer lock(@Param("lockKey") String lockKey);
}
