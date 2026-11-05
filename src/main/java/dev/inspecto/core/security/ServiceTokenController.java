package dev.inspecto.core.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.inspecto.core.common.error.ApiException;
import dev.inspecto.core.common.error.ErrorCode;
import dev.inspecto.core.common.error.FieldViolation;
import dev.inspecto.core.security.ServiceTokenService.IssuedServiceToken;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Client credentials grant for other services (platform/api/core.yaml, issueServiceToken). */
@RestController
public class ServiceTokenController {

  private static final String CLIENT_CREDENTIALS = "client_credentials";

  private final ServiceTokenService tokens;

  /** Creates the controller. */
  public ServiceTokenController(ServiceTokenService tokens) {
    this.tokens = tokens;
  }

  /** Issues a service token. */
  @PostMapping(
      path = "/internal/v1/auth/service-token",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ServiceTokenResponse issue(
      @RequestParam("grant_type") String grantType,
      @RequestParam("client_id") String clientId,
      @RequestParam("client_secret") String clientSecret) {
    if (!CLIENT_CREDENTIALS.equals(grantType)) {
      List<FieldViolation> violations = List.of(new FieldViolation("grant_type", "INVALID"));
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "Unsupported grant type", violations);
    }
    IssuedServiceToken token = tokens.issue(clientId, clientSecret);
    return new ServiceTokenResponse(token.value(), "Bearer", token.expiresInSeconds());
  }

  /** Token response in OAuth 2.0 field names. */
  public record ServiceTokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") long expiresIn) {}
}
