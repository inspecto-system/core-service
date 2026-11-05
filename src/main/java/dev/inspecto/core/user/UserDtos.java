package dev.inspecto.core.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request and response bodies of the profile API (platform/api/core.yaml, tag users). */
public final class UserDtos {

  private UserDtos() {}

  /** Public view of an account. */
  public record UserResponse(
      UUID id,
      String email,
      String fullName,
      Role role,
      String locale,
      Instant createdAt,
      long version) {

    /** Maps an entity to the response. */
    public static UserResponse of(User user) {
      return new UserResponse(
          user.getId(),
          user.getEmail(),
          user.getFullName(),
          user.getRole(),
          user.getLocale(),
          user.getCreatedAt(),
          user.getVersion());
    }
  }

  /** Profile update; absent fields keep their values. */
  public record UpdateUserRequest(
      @Size(min = 1, max = 200) String fullName,
      @Pattern(regexp = "ru|en") String locale,
      @NotNull Long version) {}

  /** Password change. */
  public record ChangePasswordRequest(
      @NotBlank @Size(max = 128) String currentPassword,
      @NotBlank @Size(min = 10, max = 128) String newPassword) {}
}
