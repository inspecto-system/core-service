package dev.inspecto.core.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the authentication API (platform/api/core.yaml). */
public final class AuthDtos {

  private AuthDtos() {}

  /** Client application type; decides how the refresh token is delivered. */
  public enum ClientType {
    @JsonProperty("web")
    WEB,
    @JsonProperty("android")
    ANDROID
  }

  /** Registration request. */
  public record RegisterRequest(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 10, max = 128) String password,
      @NotBlank @Size(max = 200) String fullName) {}

  /** Sign-in request. */
  public record LoginRequest(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 128) String password,
      @NotNull ClientType client) {}

  /** Refresh or logout request of Android clients; web clients send the cookie instead. */
  public record RefreshRequest(@Size(max = 512) String refreshToken) {}

  /** Issued tokens; {@code refreshToken} is present only for Android clients. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record TokenResponse(
      String accessToken, String tokenType, long expiresIn, String refreshToken) {}
}
