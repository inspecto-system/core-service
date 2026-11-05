package dev.inspecto.core.property;

import static org.assertj.core.api.Assertions.assertThat;

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
}
