package dev.inspecto.core.inspection;

import static dev.inspecto.core.support.Fixtures.idOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.inspecto.core.TestcontainersConfiguration;
import dev.inspecto.core.support.ApiUsers;
import dev.inspecto.core.support.Fixtures;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
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

/** Report data for report-service (T-402, core.yaml getReportData, scope reports.data). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ReportDataIntegrationTest {

  private static final String REPORT_SECRET = randomSecret();
  private static final String MEDIA_SECRET = randomSecret();

  @Autowired private MockMvc mockMvc;

  private Fixtures api;
  private String userBearer;
  private String crackId;
  private String first;
  private String second;

  @DynamicPropertySource
  static void secrets(DynamicPropertyRegistry registry) {
    registry.add("inspecto.service-clients.secrets[report-service]", () -> REPORT_SECRET);
    registry.add("inspecto.service-clients.secrets[media-service]", () -> MEDIA_SECRET);
  }

  @BeforeEach
  void createRepeatInspection() throws Exception {
    userBearer = ApiUsers.newInspector(mockMvc);
    api = new Fixtures(mockMvc, userBearer);
    String propertyId = api.property("Квартира на Мира");
    String kitchenId = api.room(propertyId, "Кухня", 0);
    crackId = api.systemCategory(Fixtures.CRACK);
    first = api.startedInspection(propertyId, "2026-10-01T09:00:00Z", List.of(kitchenId));
    second = api.startedInspection(propertyId, "2026-11-01T09:00:00Z", List.of(kitchenId));
    String defectId = idOf(api.defect(first, kitchenId, crackId, "Трещина у окна"));
    api.observation(second, defectId, "OPEN");
    api.defect(second, kitchenId, crackId, "Скол плитки");
  }

  @Test
  void shouldGiveReportServiceTheScopesOfReports() throws Exception {
    String token = serviceToken("report-service", REPORT_SECRET);

    String scopes = JsonPath.read(payloadOf(token), "$.scope");
    assertThat(scopes.split(" ")).contains("access.check", "reports.data", "photos.read");
  }

  @Test
  void shouldAssembleRepeatInspectionWithComparison() throws Exception {
    String body =
        reportData(serviceToken("report-service", REPORT_SECRET), second)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.property.name").value("Квартира на Мира"))
            .andExpect(jsonPath("$.inspection.id").value(second))
            .andExpect(jsonPath("$.inspector.fullName").value("Тест"))
            .andExpect(jsonPath("$.rooms[0].room.name").value("Кухня"))
            .andExpect(jsonPath("$.rooms[0].checklist").isNotEmpty())
            .andExpect(jsonPath("$.rooms[0].defects.length()").value(2))
            .andExpect(jsonPath("$.rooms[0].defects[0].observation.photos").isArray())
            .andExpect(jsonPath("$.summary.total").value(2))
            .andExpect(jsonPath("$.summary.bySeverity.MAJOR").value(2))
            .andExpect(jsonPath("$.summary.bySeverity.CRITICAL").value(0))
            .andExpect(jsonPath("$.comparison.base.id").value(first))
            .andExpect(jsonPath("$.comparison.summary.new").value(1))
            .andExpect(jsonPath("$.comparison.summary.remaining").value(1))
            .andExpect(jsonPath("$.generatedAt").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    String categoryName = JsonPath.read(body, "$.rooms[0].defects[0].categoryName");
    List<String> names =
        JsonPath.read(
            api.read("/api/v1/defect-categories"), "$[?(@.id == '" + crackId + "')].name");
    assertThat(categoryName).isEqualTo(names.getFirst()).isNotBlank();
  }

  @Test
  void shouldOmitComparisonForTheFirstInspection() throws Exception {
    reportData(serviceToken("report-service", REPORT_SECRET), first)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comparison").doesNotExist())
        .andExpect(jsonPath("$.summary.total").value(1));
  }

  @Test
  void shouldAdmitOnlyTokensWithTheScope() throws Exception {
    reportData(serviceToken("media-service", MEDIA_SECRET), second)
        .andExpect(status().isForbidden());
    reportData(userBearer, second).andExpect(status().isUnauthorized());
    reportData(serviceToken("report-service", REPORT_SECRET), UUID.randomUUID().toString())
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/internal/v1/inspections/" + second + "/report-data")
                .param("locale", "de")
                .header("Authorization", serviceToken("report-service", REPORT_SECRET)))
        .andExpect(status().isBadRequest());
  }

  private ResultActions reportData(String bearer, String inspectionId) throws Exception {
    return mockMvc.perform(
        get("/internal/v1/inspections/" + inspectionId + "/report-data")
            .header("Authorization", bearer));
  }

  private String serviceToken(String clientId, String secret) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/internal/v1/auth/service-token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("grant_type", "client_credentials")
                    .param("client_id", clientId)
                    .param("client_secret", secret))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return "Bearer " + JsonPath.read(body, "$.access_token");
  }

  private static String payloadOf(String bearer) {
    String jwt = bearer.substring("Bearer ".length());
    return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
  }

  private static String randomSecret() {
    return UUID.randomUUID().toString() + UUID.randomUUID();
  }
}
