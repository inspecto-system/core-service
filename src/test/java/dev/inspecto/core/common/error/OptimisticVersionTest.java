package dev.inspecto.core.common.error;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OptimisticVersionTest {

  @Test
  void shouldAcceptCurrentVersion() {
    assertThatCode(() -> OptimisticVersion.require(3, 3)).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectStaleVersion() {
    assertThatThrownBy(() -> OptimisticVersion.require(2, 1))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).code())
        .isEqualTo(ErrorCode.VERSION_CONFLICT);
  }
}
