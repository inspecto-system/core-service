package dev.inspecto.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.common.error.FieldViolation;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

  private final PasswordPolicy policy = new PasswordPolicy();

  @Test
  void shouldAcceptUncommonPassword() {
    assertThatCode(() -> policy.check("Kitchen-Window-Crack-42", "a@b.ru", "password"))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectCommonPasswordIgnoringCase() {
    assertThatThrownBy(() -> policy.check("QwertyUiop", "a@b.ru", "password"))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
              assertThat(ex.violations())
                  .containsExactly(new FieldViolation("password", "WEAK_PASSWORD"));
            });
  }

  @Test
  void shouldRejectPasswordEqualToEmail() {
    assertThatThrownBy(() -> policy.check("Inspector@Mail.ru", "inspector@mail.ru", "password"))
        .isInstanceOf(ApiException.class);
  }
}
