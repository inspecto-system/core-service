package dev.inspecto.core.outbox;

import dev.inspecto.core.common.id.UuidV7;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Records deferred work in the caller's transaction (transactional outbox, ADR-018). */
@Service
public class OutboxService {

  private final OutboxEventRepository events;
  private final JsonMapper json;
  private final Clock clock;

  /** Creates the service. */
  public OutboxService(OutboxEventRepository events, JsonMapper json, Clock clock) {
    this.events = events;
    this.json = json;
    this.clock = clock;
  }

  /** Adds an event; it is committed or rolled back together with the caller's changes. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(OutboxEventType type, Map<String, ?> payload) {
    String body = json.writeValueAsString(payload);
    events.save(new OutboxEvent(UuidV7.generate(clock), type, body, clock.instant()));
  }
}
