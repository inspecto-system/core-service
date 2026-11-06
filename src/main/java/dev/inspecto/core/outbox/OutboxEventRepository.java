package dev.inspecto.core.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

/** Persistence of outbox events. */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  /**
   * Pending jobs that are due, oldest first, locked until the end of the transaction. Jobs locked
   * by another instance are skipped ({@code FOR UPDATE SKIP LOCKED}), so instances never run the
   * same job twice.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
  @Query(
      """
      select e from OutboxEvent e
      where e.status = 'PENDING' and e.nextAttemptAt <= :now
      order by e.nextAttemptAt, e.id
      """)
  List<OutboxEvent> lockDue(@Param("now") Instant now, Limit limit);
}
