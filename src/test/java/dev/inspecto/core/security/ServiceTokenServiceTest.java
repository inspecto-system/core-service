package dev.inspecto.core.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServiceTokenServiceTest {

  @Test
  void shouldRejectShortSecretAtStartup() {
    String shortSecret = "x".repeat(ServiceTokenService.MIN_SECRET_LENGTH - 1);

    assertThatThrownBy(() -> create(Map.of("media-service", shortSecret)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("media-service")
        .hasMessageNotContaining(shortSecret);
  }

  @Test
  void shouldRejectSecretOfUnknownClient() {
    String secret = "x".repeat(ServiceTokenService.MIN_SECRET_LENGTH);

    assertThatThrownBy(() -> create(Map.of("admin-service", secret)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("admin-service");
  }

  @Test
  void shouldTreatBlankSecretAsDisabledClient() {
    assertThatCode(() -> create(Map.of("ml-service", " "))).doesNotThrowAnyException();
  }

  private static ServiceTokenService create(Map<String, String> secrets) {
    ServiceClientProperties clients =
        new ServiceClientProperties("inspecto-internal", Duration.ofMinutes(15), secrets);
    return new ServiceTokenService(null, null, null, clients, Clock.systemUTC());
  }
}
