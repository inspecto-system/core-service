package dev.inspecto.core.auth;

import dev.inspecto.core.auth.AuthDtos.ClientType;
import dev.inspecto.core.auth.AuthDtos.LoginRequest;
import dev.inspecto.core.auth.AuthDtos.RefreshRequest;
import dev.inspecto.core.auth.AuthDtos.RegisterRequest;
import dev.inspecto.core.auth.AuthDtos.TokenResponse;
import dev.inspecto.core.auth.AuthService.Tokens;
import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.security.SecurityProperties;
import dev.inspecto.core.user.UserDtos.UserResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Authentication endpoints (platform/api/core.yaml, tag auth). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  static final String REFRESH_COOKIE = "refresh_token";
  static final String CSRF_HEADER = "X-CSRF-Protection";
  private static final String COOKIE_PATH = "/api/v1/auth";
  private static final String BEARER = "Bearer";

  private final AuthService auth;
  private final SecurityProperties properties;

  /** Creates the controller. */
  public AuthController(AuthService auth, SecurityProperties properties) {
    this.auth = auth;
    this.properties = properties;
  }

  /** Registers a user. */
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse register(@Valid @RequestBody RegisterRequest request) {
    return UserResponse.of(auth.register(request.email(), request.password(), request.fullName()));
  }

  /** Signs in; web clients receive the refresh token as an HttpOnly cookie. */
  @PostMapping("/login")
  public ResponseEntity<TokenResponse> login(
      @Valid @RequestBody LoginRequest request,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    Tokens tokens = auth.login(request.email(), request.password(), userAgent);
    return respond(tokens, request.client() == ClientType.WEB);
  }

  /** Rotates the refresh token (cookie with CSRF header for web, body for Android). */
  @PostMapping("/refresh")
  public ResponseEntity<TokenResponse> refresh(
      @Valid @RequestBody(required = false) RefreshRequest request,
      @CookieValue(value = REFRESH_COOKIE, required = false) String cookie,
      @RequestHeader(value = CSRF_HEADER, required = false) String csrf,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    String bodyToken = request == null ? null : request.refreshToken();
    boolean fromCookie = bodyToken == null || bodyToken.isBlank();
    String token = fromCookie ? requireCookie(cookie, csrf) : bodyToken;
    return respond(auth.refresh(token, userAgent), fromCookie);
  }

  /** Revokes the current refresh token and clears the cookie. */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @RequestBody(required = false) RefreshRequest request,
      @CookieValue(value = REFRESH_COOKIE, required = false) String cookie) {
    String token = request == null ? null : request.refreshToken();
    String effective = token == null || token.isBlank() ? cookie : token;
    if (effective != null && !effective.isBlank()) {
      auth.logout(effective);
    }
    ResponseCookie cleared = refreshCookie("", Duration.ZERO);
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
  }

  private static String requireCookie(String cookie, String csrf) {
    if (cookie == null || cookie.isBlank()) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED, "Refresh token is missing");
    }
    if (!"1".equals(csrf)) {
      throw new ApiException(ErrorCode.FORBIDDEN, CSRF_HEADER + " header is required");
    }
    return cookie;
  }

  private ResponseEntity<TokenResponse> respond(Tokens tokens, boolean web) {
    long expiresIn = tokens.access().expiresInSeconds();
    String access = tokens.access().value();
    if (web) {
      ResponseCookie cookie = refreshCookie(tokens.refreshToken(), properties.refreshTokenTtl());
      TokenResponse body = new TokenResponse(access, BEARER, expiresIn, null);
      return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(body);
    }
    return ResponseEntity.ok(new TokenResponse(access, BEARER, expiresIn, tokens.refreshToken()));
  }

  private ResponseCookie refreshCookie(String value, Duration maxAge) {
    return ResponseCookie.from(REFRESH_COOKIE, value)
        .httpOnly(true)
        .secure(properties.cookieSecure())
        .sameSite("Strict")
        .path(COOKIE_PATH)
        .maxAge(maxAge)
        .build();
  }
}
