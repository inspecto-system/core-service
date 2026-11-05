package dev.inspecto.core.outbox;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence of outbox events. */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {}
