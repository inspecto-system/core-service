package dev.inspecto.core.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class PropertyServiceTest {

  @Test
  void shouldMatchEverythingWithoutQuery() {
    assertThat(PropertyService.likePattern(null)).isEqualTo("%");
    assertThat(PropertyService.likePattern("  ")).isEqualTo("%");
  }

  @Test
  void shouldEscapeLikeWildcards() {
    assertThat(PropertyService.likePattern(" 50%_a\\b ")).isEqualTo("%50\\%\\_a\\\\b%");
  }

  @Test
  void shouldRejectStaleVersion() {
    assertThatThrownBy(() -> PropertyService.requireVersion(2, 1))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).code())
        .isEqualTo(ErrorCode.VERSION_CONFLICT);
  }
}
