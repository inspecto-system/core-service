package dev.inspecto.core.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.inspecto.core.TestcontainersConfiguration;
import dev.inspecto.core.security.ServiceTokenService;
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

/** Calls of core-service to the internal API of media-service. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class MediaClientIntegrationTest {

  private static final FakeMedia MEDIA = FakeMedia.start();

  @Autowired private MediaClient media;
  @Autowired private ServiceTokenService tokens;
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
  void reset() {
    MEDIA.failWith(0);
    MEDIA.clearCalls();
  }

  @Test
  void shouldReadMetadataAndAttachKnownPhoto() {
    UUID owner = UUID.randomUUID();
    UUID property = UUID.randomUUID();
    UUID photo = MEDIA.upload(owner, property);

    assertThat(media.metadata(photo))
        .hasValue(new MediaClient.PhotoMetadata(photo, owner, property, "UPLOADED"));
    assertThat(media.attach(photo)).isTrue();
  }

  @Test
  void shouldReportUnknownPhotoAsMissing() {
    assertThat(media.metadata(UUID.randomUUID())).isEmpty();
    assertThat(media.attach(UUID.randomUUID())).isFalse();
  }

  @Test
  void shouldReuseOwnServiceTokenUntilItNearlyExpires() {
    media.deletePhoto(UUID.randomUUID());
    media.deletePropertyPhotos(UUID.randomUUID());

    assertThat(MEDIA.calls()).hasSize(2);
    assertThat(MEDIA.calls().get(0).tokenClaims()).isEqualTo(MEDIA.calls().get(1).tokenClaims());
  }

  @Test
  void shouldFailWhenMediaAnswersWithServerError() {
    UUID photo = MEDIA.upload(UUID.randomUUID(), UUID.randomUUID());
    MEDIA.failWith(503);

    assertThatThrownBy(() -> media.metadata(photo)).isInstanceOf(MediaUnavailableException.class);
    assertThatThrownBy(() -> media.attach(photo)).isInstanceOf(MediaUnavailableException.class);
    assertThatThrownBy(() -> media.deletePhoto(photo))
        .isInstanceOf(MediaUnavailableException.class);
  }

  @Test
  void shouldFailWhenMediaRefusesTheToken() {
    UUID photo = MEDIA.upload(UUID.randomUUID(), UUID.randomUUID());
    MEDIA.failWith(403);

    assertThatThrownBy(() -> media.metadata(photo)).isInstanceOf(MediaUnavailableException.class);
    assertThatThrownBy(() -> media.attach(photo)).isInstanceOf(MediaUnavailableException.class);
  }

  @Test
  void shouldFailWhenMediaCannotBeReached() {
    MediaClient unreachable =
        new MediaClient(
            tokens, new MediaProperties("http://127.0.0.1:1", Duration.ofSeconds(1), 10), clock);

    assertThatThrownBy(() -> unreachable.metadata(UUID.randomUUID()))
        .isInstanceOf(MediaUnavailableException.class);
    assertThatThrownBy(() -> unreachable.deletePhoto(UUID.randomUUID()))
        .isInstanceOf(MediaUnavailableException.class);
    assertThat(unreachable.maxPhotosPerObservation()).isEqualTo(10);
  }
}
