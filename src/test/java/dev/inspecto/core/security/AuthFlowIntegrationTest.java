package dev.inspecto.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.inspecto.core.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

  private static final String PASSWORD = "Kitchen-Window-Crack-42";

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private SecurityProperties properties;
  @Autowired private JwtKeyProvider keys;

  @Test
  void shouldRegisterAndRejectDuplicateEmailCaseInsensitively() throws Exception {
    String email = uniqueEmail();
    register(email)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.role").value("INSPECTOR"))
        .andExpect(jsonPath("$.password").doesNotExist());

    register(email.toUpperCase(Locale.ROOT))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
  }

  @Test
  void shouldRejectInvalidRegistrationWithFieldErrors() throws Exception {
    String body = "{\"email\":\"not-an-email\",\"password\":\"short\",\"fullName\":\"\"}";
    mockMvc
        .perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", "application/problem+json"))
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors.length()").value(3))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void shouldRejectCommonPassword() throws Exception {
    String body = registrationBody(uniqueEmail(), "qwertyuiop123");
    mockMvc
        .perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("WEAK_PASSWORD"));
  }

  @Test
  void shouldIssueRefreshTokenInBodyForAndroid() throws Exception {
    String email = registered();
    login(email, PASSWORD, "android")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(900))
        .andExpect(jsonPath("$.refreshToken").isNotEmpty())
        .andExpect(cookie().doesNotExist("refresh_token"));
  }

  @Test
  void shouldIssueRefreshTokenAsStrictHttpOnlyCookieForWeb() throws Exception {
    String email = registered();
    login(email, PASSWORD, "web")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.refreshToken").doesNotExist())
        .andExpect(cookie().httpOnly("refresh_token", true))
        .andExpect(cookie().secure("refresh_token", true))
        .andExpect(cookie().sameSite("refresh_token", "Strict"))
        .andExpect(cookie().path("refresh_token", "/api/v1/auth"));
  }

  @Test
  void shouldRejectWrongPasswordAndUnknownEmailTheSameWay() throws Exception {
    String email = registered();
    login(email, "Wrong-Password-123", "android")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    login(uniqueEmail(), PASSWORD, "android")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void shouldLockAccountAfterRepeatedFailures() throws Exception {
    String email = registered();
    for (int i = 0; i < properties.maxFailedLogins(); i++) {
      login(email, "Wrong-Password-123", "android").andExpect(status().isUnauthorized());
    }
    login(email, PASSWORD, "android")
        .andExpect(status().isLocked())
        .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
  }

  @Test
  void shouldRotateRefreshTokenAndRevokeFamilyOnReuse() throws Exception {
    String first = refreshTokenOf(login(registered(), PASSWORD, "android").andReturn());

    MvcResult rotated = refreshWithBody(first).andExpect(status().isOk()).andReturn();
    String second = refreshTokenOf(rotated);
    assertThat(second).isNotEqualTo(first);

    refreshWithBody(first)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    refreshWithBody(second).andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRequireCsrfHeaderWhenRefreshingFromCookie() throws Exception {
    MvcResult loginResult = login(registered(), PASSWORD, "web").andReturn();
    Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");

    mockMvc
        .perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mockMvc
        .perform(
            post("/api/v1/auth/refresh").cookie(refreshCookie).header("X-CSRF-Protection", "1"))
        .andExpect(status().isOk())
        .andExpect(cookie().exists("refresh_token"));
  }

  @Test
  void shouldRevokeRefreshTokenOnLogout() throws Exception {
    MvcResult loginResult = login(registered(), PASSWORD, "android").andReturn();
    String refresh = refreshTokenOf(loginResult);

    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .header("Authorization", bearer(loginResult))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
        .andExpect(status().isNoContent());
    refreshWithBody(refresh).andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturnProfileOnlyWithValidToken() throws Exception {
    String email = registered();
    MvcResult loginResult = login(email, PASSWORD, "android").andReturn();

    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", bearer(loginResult)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email));
    mockMvc
        .perform(get("/api/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReportExpiredToken() throws Exception {
    Instant past = Instant.now().minusSeconds(3_600);
    String expired = token(past, past.plusSeconds(60), properties.audience());

    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + expired))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
  }

  @Test
  void shouldRejectTokenForAnotherAudience() throws Exception {
    Instant now = Instant.now();
    String internal = token(now, now.plusSeconds(600), "inspecto-internal");

    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + internal))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectStaleVersionOnProfileUpdate() throws Exception {
    MvcResult loginResult = login(registered(), PASSWORD, "android").andReturn();

    mockMvc
        .perform(
            patch("/api/v1/users/me")
                .header("Authorization", bearer(loginResult))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Новое Имя\",\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Новое Имя"));
    mockMvc
        .perform(
            patch("/api/v1/users/me")
                .header("Authorization", bearer(loginResult))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"locale\":\"en\",\"version\":0}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
  }

  @Test
  void shouldRevokeSessionsAfterPasswordChange() throws Exception {
    String email = registered();
    MvcResult loginResult = login(email, PASSWORD, "android").andReturn();
    String refresh = refreshTokenOf(loginResult);
    String body =
        "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Ceiling-Stain-77-x\"}";

    mockMvc
        .perform(
            post("/api/v1/users/me/password")
                .header("Authorization", bearer(loginResult))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNoContent());
    refreshWithBody(refresh).andExpect(status().isUnauthorized());
    login(email, "Ceiling-Stain-77-x", "android").andExpect(status().isOk());
  }

  @Test
  void shouldPublishPublicKeyOnly() throws Exception {
    mockMvc
        .perform(get("/api/v1/.well-known/jwks.json"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
        .andExpect(jsonPath("$.keys[0].kid").value(keys.signingKey().getKeyID()))
        .andExpect(jsonPath("$.keys[0].d").doesNotExist());
  }

  private ResultActions register(String email) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(registrationBody(email, PASSWORD)));
  }

  private ResultActions login(String email, String password, String client) throws Exception {
    String body =
        "{\"email\":\""
            + email
            + "\",\"password\":\""
            + password
            + "\",\"client\":\""
            + client
            + "\"}";
    return mockMvc.perform(
        post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions refreshWithBody(String refreshToken) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
  }

  private String registered() throws Exception {
    String email = uniqueEmail();
    register(email).andExpect(status().isCreated());
    return email;
  }

  private String token(Instant issuedAt, Instant expiresAt, String audience) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .audience(List.of(audience))
            .subject(UUID.randomUUID().toString())
            .claim("role", "INSPECTOR")
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .build();
    JwsHeader header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(keys.signingKey().getKeyID()).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private static String bearer(MvcResult loginResult) throws UnsupportedEncodingException {
    String body = loginResult.getResponse().getContentAsString();
    return "Bearer " + JsonPath.read(body, "$.accessToken");
  }

  private static String refreshTokenOf(MvcResult result) throws UnsupportedEncodingException {
    return JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken");
  }

  private static String registrationBody(String email, String password) {
    return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"fullName\":\"Тест\"}";
  }

  private static String uniqueEmail() {
    return "user-" + UUID.randomUUID() + "@example.com";
  }
}
