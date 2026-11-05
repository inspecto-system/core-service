package dev.inspecto.core.property;

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

/** Access rules of every property and room endpoint (T-102, TESTING.md SEC-01). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PropertyAccessIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private SecurityProperties properties;

  private String owner;
  private String propertyId;
  private String roomId;

  /** Endpoint addressing a property ({p}) or a room ({r}). */
  record Endpoint(HttpMethod method, String path, String body) {

    MockHttpServletRequestBuilder request(String property, String room) {
      String uri = "/api/v1" + path.replace("{p}", property).replace("{r}", room);
      return MockMvcRequestBuilders.request(method, uri)
          .contentType(MediaType.APPLICATION_JSON)
          .content(body);
    }

    @Override
    public String toString() {
      return method + " " + path;
    }
  }

  static Stream<Endpoint> resourceEndpoints() {
    return Stream.of(
        new Endpoint(HttpMethod.GET, "/properties/{p}", ""),
        new Endpoint(HttpMethod.PATCH, "/properties/{p}", "{\"name\":\"Чужое\",\"version\":0}"),
        new Endpoint(HttpMethod.DELETE, "/properties/{p}", ""),
        new Endpoint(HttpMethod.POST, "/properties/{p}/archive", ""),
        new Endpoint(HttpMethod.POST, "/properties/{p}/unarchive", ""),
        new Endpoint(HttpMethod.GET, "/properties/{p}/rooms", ""),
        new Endpoint(
            HttpMethod.POST,
            "/properties/{p}/rooms",
            "{\"name\":\"Чужая\",\"roomType\":\"OTHER\"}"),
        new Endpoint(HttpMethod.PATCH, "/rooms/{r}", "{\"name\":\"Чужая\",\"version\":0}"),
        new Endpoint(HttpMethod.DELETE, "/rooms/{r}", ""));
  }

  static Stream<Endpoint> allEndpoints() {
    return Stream.concat(
        Stream.of(
            new Endpoint(HttpMethod.GET, "/properties", ""),
            new Endpoint(
                HttpMethod.POST,
                "/properties",
                "{\"name\":\"x\",\"type\":\"HOUSE\",\"address\":\"y\"}")),
        resourceEndpoints());
  }

  @BeforeEach
  void createOwnPropertyWithRoom() throws Exception {
    owner = ApiUsers.newInspector(mockMvc);
    String property =
        "{\"name\":\"Моя квартира\",\"type\":\"APARTMENT\",\"address\":\"ул. Мира, 5\"}";
    propertyId = idOf(created("/api/v1/properties", property));
    roomId =
        idOf(
            created(
                "/api/v1/properties/" + propertyId + "/rooms",
                "{\"name\":\"Кухня\",\"roomType\":\"KITCHEN\"}"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("resourceEndpoints")
  void shouldHideResourcesOfAnotherUser(Endpoint endpoint) throws Exception {
    String intruder = ApiUsers.newInspector(mockMvc);

    mockMvc
        .perform(endpoint.request(propertyId, roomId).header("Authorization", intruder))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    mockMvc
        .perform(get("/api/v1/properties/" + propertyId).header("Authorization", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Моя квартира"))
        .andExpect(jsonPath("$.archivedAt").isEmpty())
        .andExpect(jsonPath("$.version").value(0))
        .andExpect(jsonPath("$.rooms.length()").value(1))
        .andExpect(jsonPath("$.rooms[0].name").value("Кухня"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("resourceEndpoints")
  void shouldReportMissingResourcesAsNotFound(Endpoint endpoint) throws Exception {
    String missing = UUID.randomUUID().toString();
    mockMvc
        .perform(endpoint.request(missing, missing).header("Authorization", owner))
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("allEndpoints")
  void shouldRequireAuthentication(Endpoint endpoint) throws Exception {
    mockMvc
        .perform(endpoint.request(propertyId, roomId))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("allEndpoints")
  void shouldForbidRolesWithoutPropertyManagement(Endpoint endpoint) throws Exception {
    mockMvc
        .perform(endpoint.request(propertyId, roomId).header("Authorization", clientToken()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void shouldNotTakeOwnerFromRequestBody() throws Exception {
    String intruder = ApiUsers.newInspector(mockMvc);
    String body =
        "{\"name\":\"Подмена\",\"type\":\"HOUSE\",\"address\":\"z\",\"ownerId\":\""
            + UUID.randomUUID()
            + "\"}";
    created("/api/v1/properties", body, intruder);

    mockMvc
        .perform(get("/api/v1/properties").header("Authorization", owner))
        .andExpect(jsonPath("$.total").value(1));
    mockMvc
        .perform(get("/api/v1/properties").header("Authorization", intruder))
        .andExpect(jsonPath("$.items[0].name").value("Подмена"));
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

  private String created(String path, String body) throws Exception {
    return created(path, body, owner);
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
