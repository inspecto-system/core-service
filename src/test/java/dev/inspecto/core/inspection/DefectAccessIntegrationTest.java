package dev.inspecto.core.inspection;

import static dev.inspecto.core.support.Fixtures.idOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.inspecto.core.TestcontainersConfiguration;
import dev.inspecto.core.security.SecurityProperties;
import dev.inspecto.core.support.ApiUsers;
import dev.inspecto.core.support.Fixtures;
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

/** Access rules of every defect and observation endpoint (TESTING.md SEC-01). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DefectAccessIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private SecurityProperties properties;

  private Fixtures owner;
  private Ids ids;

  /** Identifiers substituted into endpoint paths and bodies. */
  record Ids(
      String property,
      String room,
      String category,
      String inspection,
      String defect,
      String observation) {}

  /**
   * Endpoint addressing a property ({p}), an inspection ({i}), a defect ({d}) or an observation
   * ({o}); {r}, {c} and {d} in the body are a room, a category and a defect of the owner.
   */
  record Endpoint(HttpMethod method, String path, String body) {

    MockHttpServletRequestBuilder request(Ids ids) {
      String uri =
          "/api/v1"
              + path.replace("{p}", ids.property())
                  .replace("{i}", ids.inspection())
                  .replace("{d}", ids.defect())
                  .replace("{o}", ids.observation());
      String content =
          body.replace("{r}", ids.room())
              .replace("{c}", ids.category())
              .replace("{d}", ids.defect());
      return MockMvcRequestBuilders.request(method, uri)
          .contentType(MediaType.APPLICATION_JSON)
          .content(content);
    }

    @Override
    public String toString() {
      return method + " " + path;
    }
  }

  static Stream<Endpoint> endpoints() {
    return Stream.of(
        new Endpoint(HttpMethod.GET, "/properties/{p}/defects", ""),
        new Endpoint(
            HttpMethod.POST,
            "/inspections/{i}/defects",
            "{\"roomId\":\"{r}\",\"categoryId\":\"{c}\",\"elementGroup\":\"WALLS\","
                + "\"title\":\"Чужой\",\"severity\":\"MINOR\"}"),
        new Endpoint(HttpMethod.GET, "/defects/{d}", ""),
        new Endpoint(HttpMethod.PATCH, "/defects/{d}", "{\"title\":\"Чужой\",\"version\":0}"),
        new Endpoint(HttpMethod.GET, "/defects/{d}/history", ""),
        new Endpoint(HttpMethod.GET, "/inspections/{i}/open-defects", ""),
        new Endpoint(
            HttpMethod.POST,
            "/inspections/{i}/observations",
            "{\"defectId\":\"{d}\",\"status\":\"RESOLVED\"}"),
        new Endpoint(
            HttpMethod.PATCH, "/observations/{o}", "{\"status\":\"RESOLVED\",\"version\":0}"),
        new Endpoint(
            HttpMethod.POST,
            "/observations/{o}/photos",
            "{\"photoId\":\"" + UUID.randomUUID() + "\"}"),
        new Endpoint(HttpMethod.DELETE, "/observations/{o}/photos/" + UUID.randomUUID(), ""));
  }

  @BeforeEach
  void createOwnDefect() throws Exception {
    owner = new Fixtures(mockMvc, ApiUsers.newInspector(mockMvc));
    String property = owner.property("Моя квартира");
    String room = owner.room(property, "Кухня", 0);
    String category = owner.systemCategory(Fixtures.CRACK);
    String inspection = owner.startedInspection(property, "2026-11-05T09:00:00Z", List.of(room));
    String defect = owner.defect(inspection, room, category, "Трещина");
    ids =
        new Ids(
            property,
            room,
            category,
            inspection,
            idOf(defect),
            JsonPath.read(defect, "$.observations[0].id"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("endpoints")
  void shouldHideResourcesOfAnotherUser(Endpoint endpoint) throws Exception {
    String intruder = ApiUsers.newInspector(mockMvc);

    mockMvc
        .perform(endpoint.request(ids).header("Authorization", intruder))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    owner
        .send(get("/api/v1/defects/" + ids.defect()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Трещина"))
        .andExpect(jsonPath("$.currentStatus").value("OPEN"))
        .andExpect(jsonPath("$.version").value(0))
        .andExpect(jsonPath("$.observations.length()").value(1))
        .andExpect(jsonPath("$.observations[0].version").value(0));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("endpoints")
  void shouldReportMissingResourcesAsNotFound(Endpoint endpoint) throws Exception {
    String missing = UUID.randomUUID().toString();
    Ids missingIds = new Ids(missing, ids.room(), ids.category(), missing, missing, missing);
    owner.send(endpoint.request(missingIds)).andExpect(status().isNotFound());
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
  void shouldNotTakeStatusOrAuthorFromRequestBody() throws Exception {
    String body =
        "{\"roomId\":\""
            + ids.room()
            + "\",\"categoryId\":\""
            + ids.category()
            + "\",\"elementGroup\":\"FLOOR\",\"title\":\"Скол\",\"severity\":\"MINOR\","
            + "\"currentStatus\":\"RESOLVED\",\"propertyId\":\""
            + UUID.randomUUID()
            + "\"}";

    String created = owner.created("/api/v1/inspections/" + ids.inspection() + "/defects", body);

    owner
        .send(get("/api/v1/defects/" + idOf(created)))
        .andExpect(jsonPath("$.currentStatus").value("OPEN"))
        .andExpect(jsonPath("$.propertyId").value(ids.property()));
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
}
