package dev.inspecto.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.inspecto.core.TestcontainersConfiguration;
import dev.inspecto.core.support.ApiUsers;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Service tokens and the internal access API (T-105, SECURITY.md §8). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ServiceTokenIntegrationTest {

  private static final String MEDIA_SECRET = randomSecret();
  private static final String ML_SECRET = randomSecret();
  private static final String TOKEN = "/internal/v1/auth/service-token";
  private static final String ACCESS = "/internal/v1/access/properties/";

  @Autowired private MockMvc mockMvc;

  private String owner;
  private String propertyId;

  @DynamicPropertySource
  static void serviceSecrets(DynamicPropertyRegistry registry) {
    registry.add("inspecto.service-clients.secrets[media-service]", () -> MEDIA_SECRET);
    registry.add("inspecto.service-clients.secrets[ml-service]", () -> ML_SECRET);
  }

  @BeforeEach
  void createProperty() throws Exception {
    owner = ApiUsers.newInspector(mockMvc);
    String body =
        mockMvc
            .perform(
                post("/api/v1/properties")
                    .header("Authorization", owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"Квартира\",\"type\":\"APARTMENT\",\"address\":\"ул. Мира, 1\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    propertyId = JsonPath.read(body, "$.id");
  }

  @Test
  void shouldIssueServiceTokenForInternalAudience() throws Exception {
    String body =
        requestToken("client_credentials", "media-service", MEDIA_SECRET)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.expires_in").value(900))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String payload = payloadOf(JsonPath.read(body, "$.access_token"));
    // A single audience is serialized as a string (RFC 7519 §4.1.3).
    assertThat((String) JsonPath.read(payload, "$.aud")).isEqualTo("inspecto-internal");
    assertThat((String) JsonPath.read(payload, "$.sub")).isEqualTo("media-service");
    assertThat((String) JsonPath.read(payload, "$.scope")).isEqualTo("access.check");
  }

  @Test
  void shouldGrantMlServiceOnlyPhotoReading() throws Exception {
    String body =
        requestToken("client_credentials", "ml-service", ML_SECRET)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String payload = payloadOf(JsonPath.read(body, "$.access_token"));
    assertThat((String) JsonPath.read(payload, "$.sub")).isEqualTo("ml-service");
    assertThat((String) JsonPath.read(payload, "$.scope")).isEqualTo("photos.read");
  }

  @Test
  void shouldRejectWrongOrUnknownClientCredentials() throws Exception {
    requestToken("client_credentials", "media-service", MEDIA_SECRET + "x")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    requestToken("client_credentials", "admin-service", MEDIA_SECRET)
        .andExpect(status().isUnauthorized());
    // report-service has no secret configured in this test, so it cannot obtain tokens at all.
    requestToken("client_credentials", "report-service", "").andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectOtherGrantTypes() throws Exception {
    requestToken("password", "media-service", MEDIA_SECRET)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("grant_type"));
  }

  @Test
  void shouldAllowAccessOfOwnerOnly() throws Exception {
    String service = serviceToken("media-service", MEDIA_SECRET);
    String ownerId = userIdOf(owner);

    checkAccess(service, propertyId, ownerId, "read")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allowed").value(true));
    checkAccess(service, propertyId, ownerId, "write").andExpect(status().isOk());
    checkAccess(service, propertyId, userIdOf(ApiUsers.newInspector(mockMvc)), "read")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    checkAccess(service, UUID.randomUUID().toString(), ownerId, "read")
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldValidateAccessCheckParameters() throws Exception {
    String service = serviceToken("media-service", MEDIA_SECRET);

    checkAccess(service, propertyId, userIdOf(owner), "delete")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mockMvc
        .perform(get(ACCESS + propertyId).param("action", "read").header("Authorization", service))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldNotAcceptUserTokensOnInternalEndpoints() throws Exception {
    checkAccess(owner, propertyId, userIdOf(owner), "read")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    mockMvc
        .perform(get(ACCESS + propertyId).param("userId", userIdOf(owner)).param("action", "read"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldNotAcceptServiceTokensOnPublicApi() throws Exception {
    String service = serviceToken("media-service", MEDIA_SECRET);

    mockMvc
        .perform(get("/api/v1/properties/" + propertyId).header("Authorization", service))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldForbidAccessCheckWithoutScope() throws Exception {
    String ml = serviceToken("ml-service", ML_SECRET);

    checkAccess(ml, propertyId, userIdOf(owner), "read")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  private ResultActions requestToken(String grantType, String clientId, String secret)
      throws Exception {
    return mockMvc.perform(
        post(TOKEN)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", grantType)
            .param("client_id", clientId)
            .param("client_secret", secret));
  }

  private String serviceToken(String clientId, String secret) throws Exception {
    String body =
        requestToken("client_credentials", clientId, secret)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return "Bearer " + JsonPath.read(body, "$.access_token");
  }

  private ResultActions checkAccess(String bearer, String property, String userId, String action)
      throws Exception {
    return mockMvc.perform(
        get(ACCESS + property)
            .param("userId", userId)
            .param("action", action)
            .header("Authorization", bearer));
  }

  private static String userIdOf(String bearer) {
    return JsonPath.read(payloadOf(bearer.substring("Bearer ".length())), "$.sub");
  }

  private static String payloadOf(String jwt) {
    return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
  }

  private static String randomSecret() {
    return UUID.randomUUID().toString() + UUID.randomUUID();
  }
}
