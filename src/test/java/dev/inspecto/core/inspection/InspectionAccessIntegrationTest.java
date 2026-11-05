package dev.inspecto.core.inspection;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.inspecto.core.TestcontainersConfiguration;
import dev.inspecto.core.security.SecurityProperties;
import dev.inspecto.core.support.ApiUsers;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Access rules of every inspection and checklist endpoint (T-102 rules, TESTING.md SEC-01). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InspectionAccessIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private SecurityProperties properties;

  private String owner;
  private Ids ids;

  /** Identifiers substituted into endpoint paths. */
  record Ids(String property, String room, String inspection, String item) {}

  /**
   * Endpoint addressing a property ({p}), an inspection ({i}) or a checklist item ({c}); {r} in the
   * body is a room of the property.
   */
  record Endpoint(HttpMethod method, String path, String body) {

    MockHttpServletRequestBuilder request(Ids ids) {
      String uri =
          "/api/v1"
              + path.replace("{p}", ids.property())
                  .replace("{i}", ids.inspection())
                  .replace("{c}", ids.item());
      return MockMvcRequestBuilders.request(method, uri)
          .contentType(MediaType.APPLICATION_JSON)
          .content(body.replace("{r}", ids.room()));
    }

    @Override
    public String toString() {
      return method + " " + path;
    }
  }

  static Stream<Endpoint> endpoints() {
    return Stream.of(
        new Endpoint(HttpMethod.GET, "/properties/{p}/inspections", ""),
        new Endpoint(
            HttpMethod.POST,
            "/properties/{p}/inspections",
            "{\"inspectedAt\":\"2026-11-05T09:00:00Z\",\"roomIds\":[\"{r}\"],"
                + "\"templateCodes\":[\"ROOM_STANDARD\"]}"),
        new Endpoint(HttpMethod.GET, "/inspections/{i}", ""),
        new Endpoint(HttpMethod.PATCH, "/inspections/{i}", "{\"title\":\"Чужой\",\"version\":0}"),
        new Endpoint(HttpMethod.POST, "/inspections/{i}/complete", "{\"version\":0}"),
        new Endpoint(HttpMethod.DELETE, "/inspections/{i}", ""),
        new Endpoint(
            HttpMethod.POST,
            "/inspections/{i}/checklist-items",
            "{\"roomId\":\"{r}\",\"elementGroup\":\"OTHER\",\"title\":\"Чужой\"}"),
        new Endpoint(
            HttpMethod.PATCH, "/checklist-items/{c}", "{\"result\":\"OK\",\"version\":0}"));
  }

  @BeforeEach
  void createOwnInspection() throws Exception {
    owner = ApiUsers.newInspector(mockMvc);
    String property =
        idOf(
            created(
                "/api/v1/properties",
                "{\"name\":\"Моя квартира\",\"type\":\"APARTMENT\",\"address\":\"ул. Мира, 5\"}",
                owner));
    String room =
        idOf(
            created(
                "/api/v1/properties/" + property + "/rooms",
                "{\"name\":\"Кухня\",\"roomType\":\"KITCHEN\"}",
                owner));
    String inspection =
        created(
            "/api/v1/properties/" + property + "/inspections",
            "{\"inspectedAt\":\"2026-11-05T09:00:00Z\",\"roomIds\":[\""
                + room
                + "\"],\"templateCodes\":[\"ROOM_STANDARD\"]}",
            owner);
    ids =
        new Ids(
            property,
            room,
            idOf(inspection),
            JsonPath.read(inspection, "$.rooms[0].checklist[0].id"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("endpoints")
  void shouldHideResourcesOfAnotherUser(Endpoint endpoint) throws Exception {
    String intruder = ApiUsers.newInspector(mockMvc);

    mockMvc
        .perform(endpoint.request(ids).header("Authorization", intruder))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    mockMvc
        .perform(get("/api/v1/inspections/" + ids.inspection()).header("Authorization", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").isEmpty())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.version").value(0))
        .andExpect(jsonPath("$.rooms[0].checklist[0].result").value("NOT_CHECKED"))
        .andExpect(jsonPath("$.rooms[0].checklist.length()").value(8));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("endpoints")
  void shouldReportMissingResourcesAsNotFound(Endpoint endpoint) throws Exception {
    String missing = UUID.randomUUID().toString();
    Ids missingIds = new Ids(missing, ids.room(), missing, missing);
    mockMvc
        .perform(endpoint.request(missingIds).header("Authorization", owner))
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("endpoints")
  void shouldRequireAuthentication(Endpoint endpoint) throws Exception {
    mockMvc
        .perform(endpoint.request(ids))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("endpoints")
  void shouldForbidRolesWithoutPropertyManagement(Endpoint endpoint) throws Exception {
    mockMvc
        .perform(endpoint.request(ids).header("Authorization", clientToken()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void shouldNotTakeInspectorFromRequestBody() throws Exception {
    String body =
        "{\"inspectedAt\":\"2026-11-05T09:00:00Z\",\"roomIds\":[\""
            + ids.room()
            + "\"],\"templateCodes\":[\"ROOM_STANDARD\"],\"inspectorId\":\""
            + UUID.randomUUID()
            + "\",\"status\":\"COMPLETED\"}";
    String first =
        mockMvc
            .perform(get("/api/v1/inspections/" + ids.inspection()).header("Authorization", owner))
            .andReturn()
            .getResponse()
            .getContentAsString();

    mockMvc
        .perform(
            post("/api/v1/properties/" + ids.property() + "/inspections")
                .header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.inspectorId").value(JsonPath.<String>read(first, "$.inspectorId")))
        .andExpect(jsonPath("$.status").value("DRAFT"));
  }

  private String clientToken() {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .audience(List.of(properties.audience()))
            .subject(UUID.randomUUID().toString())
            .claim("role", "CLIENT")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(60))
            .build();
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.keyId()).build();
    return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private String created(String path, String body, String bearer) throws Exception {
    return mockMvc
        .perform(
            post(path)
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static String idOf(String json) {
    return JsonPath.read(json, "$.id");
  }
}
