package dev.inspecto.core.user;

import dev.inspecto.core.user.UserDtos.ChangePasswordRequest;
import dev.inspecto.core.user.UserDtos.UpdateUserRequest;
import dev.inspecto.core.user.UserDtos.UserResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Profile endpoints of the signed-in user (platform/api/core.yaml, tag users). */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

  private final UserService users;

  /** Creates the controller. */
  public UserController(UserService users) {
    this.users = users;
  }

  /** Current profile. */
  @GetMapping
  public UserResponse get(@AuthenticationPrincipal Jwt jwt) {
    return UserResponse.of(users.get(userId(jwt)));
  }

  /** Updates the profile. */
  @PatchMapping
  public UserResponse update(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateUserRequest request) {
    UUID id = userId(jwt);
    return UserResponse.of(
        users.update(id, request.fullName(), request.locale(), request.version()));
  }

  /** Changes the password and revokes all sessions. */
  @PostMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
    users.changePassword(userId(jwt), request.currentPassword(), request.newPassword());
  }

  private static UUID userId(Jwt jwt) {
    // The decoder accepts only tokens signed by core, and core always sets the subject.
    return UUID.fromString(Objects.requireNonNull(jwt.getSubject(), "Access token has no subject"));
  }
}
