package dev.inspecto.core.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.inspecto.core.TestcontainersConfiguration;
import dev.inspecto.core.common.id.UuidV7;
import dev.inspecto.core.support.FakeMedia;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Delivery of clean-up jobs to media-service (ADR-018, T-206). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OutboxProcessorIntegrationTest {

  private static final FakeMedia MEDIA = FakeMedia.start();

  @Autowired private OutboxProcessor processor;
  @Autowired private OutboxEventRepository events;
  @Autowired private Clock clock;

  @DynamicPropertySource
  static void media(DynamicPropertyRegistry registry) {
    registry.add("inspecto.media.url", MEDIA::url);
  }

  @AfterAll
  static void stopMedia() {
    MEDIA.close();
  }

  @BeforeEach
  void startClean() {
    events.deleteAll();
    MEDIA.failWith(0);
    MEDIA.clearCalls();
  }

  private OutboxEvent enqueue(OutboxEventType type, String payload) {
    return events.save(new OutboxEvent(UuidV7.generate(clock), type, payload, clock.instant()));
  }

  @Test
  void shouldDeliverDuePhotoPropertyAndAccountDeletions() {
    UUID photo = UUID.randomUUID();
    UUID property = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    OutboxEvent single = enqueue(OutboxEventType.DELETE_PHOTO, "{\"photoId\":\"" + photo + "\"}");
    OutboxEvent bulk =
        enqueue(OutboxEventType.DELETE_PROPERTY_FILES, "{\"propertyId\":\"" + property + "\"}");
    OutboxEvent account =
        enqueue(OutboxEventType.DELETE_USER_FILES, "{\"ownerId\":\"" + owner + "\"}");

    assertThat(processor.processDue()).isEqualTo(3);

    assertThat(MEDIA.calls())
        .extracting(c -> c.method() + " " + c.path())
        .containsExactlyInAnyOrder(
            "DELETE /internal/v1/photos/" + photo,
            "DELETE /internal/v1/photos?propertyId=" + property,
            "DELETE /internal/v1/photos?ownerId=" + owner);
    assertThat(events.findById(account.getId()))
        .get()
        .extracting(OutboxEvent::getStatus)
        .isEqualTo("DONE");
    assertThat(events.findById(single.getId()))
        .get()
        .extracting(OutboxEvent::getStatus)
        .isEqualTo("DONE");
    assertThat(events.findById(bulk.getId()))
        .get()
        .extracting(OutboxEvent::getStatus)
        .isEqualTo("DONE");
    assertThat(processor.processDue()).isZero();
  }

  @Test
  void shouldRetryFailedJobLaterAndKeepIt() {
    OutboxEvent event =
        enqueue(OutboxEventType.DELETE_PHOTO, "{\"photoId\":\"" + UUID.randomUUID() + "\"}");
    MEDIA.failWith(503);

    assertThat(processor.processDue()).isZero();

    OutboxEvent failed = events.findById(event.getId()).orElseThrow();
    assertThat(failed.getStatus()).isEqualTo("PENDING");
    assertThat(failed.getAttempts()).isEqualTo(1);
    assertThat(failed.getLastError()).startsWith("MediaUnavailableException");
    assertThat(failed.getNextAttemptAt()).isAfter(clock.instant().plusSeconds(5));
    MEDIA.clearCalls();
    MEDIA.failWith(0);
    assertThat(processor.processDue()).as("not due yet").isZero();
    assertThat(MEDIA.calls()).isEmpty();
  }

  @Test
  void shouldKeepJobWithMalformedPayloadForInvestigation() {
    OutboxEvent event = enqueue(OutboxEventType.DELETE_PHOTO, "{\"photo\":42}");

    assertThat(processor.processDue()).isZero();

    OutboxEvent kept = events.findById(event.getId()).orElseThrow();
    assertThat(kept.getStatus()).isEqualTo("PENDING");
    assertThat(kept.getLastError()).contains("photoId");
  }

  @Test
  void shouldDoubleRetryDelayUpToTheLimit() {
    assertThat(processor.retryDelay(0)).isEqualTo(Duration.ofSeconds(10));
    assertThat(processor.retryDelay(1)).isEqualTo(Duration.ofSeconds(20));
    assertThat(processor.retryDelay(5)).isEqualTo(Duration.ofSeconds(320));
    assertThat(processor.retryDelay(9)).isEqualTo(Duration.ofHours(1));
    assertThat(processor.retryDelay(1000)).isEqualTo(Duration.ofHours(1));
  }
}
