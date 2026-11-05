package dev.inspecto.core.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.inspecto.core.TestcontainersConfiguration;
import dev.inspecto.core.outbox.OutboxEvent;
import dev.inspecto.core.outbox.OutboxEventRepository;
import dev.inspecto.core.outbox.OutboxEventType;
import dev.inspecto.core.support.ApiUsers;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PropertyApiIntegrationTest {

  private static final String PROPERTIES = "/api/v1/properties";
  private static final String FLAT =
      "{\"name\":\"  Квартира на Ленина \",\"type\":\"APARTMENT\",\"address\":\"ул. Ленина, 1\","
          + "\"city\":\"Казань\",\"areaSqm\":54.3,\"notes\":\"Новостройка\"}";

  @Autowired private MockMvc mockMvc;
  @Autowired private OutboxEventRepository outboxEvents;

  private String owner;

  @BeforeEach
  void signIn() throws Exception {
    owner = ApiUsers.newInspector(mockMvc);
  }

  @Test
  void shouldCreatePropertyAndShowEmptyCard() throws Exception {
    String id = createProperty(owner, FLAT);

    mockMvc
        .perform(get(PROPERTIES + "/" + id).header("Authorization", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Квартира на Ленина"))
        .andExpect(jsonPath("$.type").value("APARTMENT"))
        .andExpect(jsonPath("$.areaSqm").value(54.3))
        .andExpect(jsonPath("$.archivedAt").value(nullValue()))
        .andExpect(jsonPath("$.version").value(0))
        .andExpect(jsonPath("$.rooms").isEmpty())
        .andExpect(jsonPath("$.inspectionCount").value(0))
        .andExpect(jsonPath("$.openDefectCount").value(0));
  }

  @Test
  void shouldRejectInvalidPropertyWithFieldErrors() throws Exception {
    String body =
        "{\"name\":\" \",\"type\":\"APARTMENT\",\"address\":\"a\",\"areaSqm\":100000.001}";
    postJson(owner, PROPERTIES, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "areaSqm", "areaSqm")));
  }

  @Test
  void shouldRejectUnknownPropertyType() throws Exception {
    postJson(owner, PROPERTIES, FLAT.replace("APARTMENT", "CASTLE"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void shouldListOwnPropertiesNewestFirstWithSearchAndPaging() throws Exception {
    createProperty(owner, FLAT);
    createProperty(owner, FLAT.replace("Квартира на Ленина", "Дача 100% готова"));
    createProperty(ApiUsers.newInspector(mockMvc), FLAT);

    String all =
        mockMvc
            .perform(get(PROPERTIES).header("Authorization", owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(
                jsonPath(
                    "$.items[*].name",
                    containsInAnyOrder("Дача 100% готова", "Квартира на Ленина")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> createdAt = JsonPath.read(all, "$.items[*].createdAt");
    assertThat(createdAt).isSortedAccordingTo(Comparator.comparing(Instant::parse).reversed());
    mockMvc
        .perform(get(PROPERTIES).param("query", "ДАЧА").header("Authorization", owner))
        .andExpect(jsonPath("$.total").value(1));
    mockMvc
        .perform(get(PROPERTIES).param("query", "0%").header("Authorization", owner))
        .andExpect(jsonPath("$.items[0].name").value("Дача 100% готова"))
        .andExpect(jsonPath("$.total").value(1));
    mockMvc
        .perform(get(PROPERTIES).param("query", "ленина, 1").header("Authorization", owner))
        .andExpect(jsonPath("$.total").value(2));
    mockMvc
        .perform(
            get(PROPERTIES).param("size", "1").param("page", "1").header("Authorization", owner))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.items[0].id").value(JsonPath.<String>read(all, "$.items[1].id")));
  }

  @Test
  void shouldRejectOutOfRangePaging() throws Exception {
    mockMvc
        .perform(get(PROPERTIES).param("size", "101").header("Authorization", owner))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mockMvc
        .perform(get(PROPERTIES).param("page", "-1").header("Authorization", owner))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldUpdateOnlyGivenFieldsAndClearExplicitNulls() throws Exception {
    String id = createProperty(owner, FLAT);

    patchJson(owner, PROPERTIES + "/" + id, "{\"name\":\"Новое имя\",\"city\":null,\"version\":0}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Новое имя"))
        .andExpect(jsonPath("$.city").value(nullValue()))
        .andExpect(jsonPath("$.address").value("ул. Ленина, 1"))
        .andExpect(jsonPath("$.areaSqm").value(54.3))
        .andExpect(jsonPath("$.notes").value("Новостройка"))
        .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  void shouldRejectStaleVersionAndBlankName() throws Exception {
    String id = createProperty(owner, FLAT);

    patchJson(owner, PROPERTIES + "/" + id, "{\"notes\":\"x\",\"version\":3}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    patchJson(owner, PROPERTIES + "/" + id, "{\"name\":\"   \",\"version\":0}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("name"));
    patchJson(owner, PROPERTIES + "/" + id, "{\"name\":\"x\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("version"));
  }

  @Test
  void shouldArchiveAndRestoreProperty() throws Exception {
    String id = createProperty(owner, FLAT);

    postJson(owner, PROPERTIES + "/" + id + "/archive", "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").isNotEmpty());
    postJson(owner, PROPERTIES + "/" + id + "/archive", "").andExpect(status().isOk());
    mockMvc
        .perform(get(PROPERTIES).header("Authorization", owner))
        .andExpect(jsonPath("$.total").value(0));
    mockMvc
        .perform(get(PROPERTIES).param("archived", "true").header("Authorization", owner))
        .andExpect(jsonPath("$.items[0].id").value(id));

    postJson(owner, PROPERTIES + "/" + id + "/unarchive", "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").value(nullValue()));
    mockMvc
        .perform(get(PROPERTIES).header("Authorization", owner))
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void shouldDeletePropertyWithRoomsAndScheduleFileCleanup() throws Exception {
    String id = createProperty(owner, FLAT);
    String roomId = createRoom(owner, id, "{\"name\":\"Кухня\",\"roomType\":\"KITCHEN\"}");

    mockMvc
        .perform(delete(PROPERTIES + "/" + id).header("Authorization", owner))
        .andExpect(status().isAccepted());

    mockMvc
        .perform(get(PROPERTIES + "/" + id).header("Authorization", owner))
        .andExpect(status().isNotFound());
    patchJson(owner, "/api/v1/rooms/" + roomId, "{\"name\":\"x\",\"version\":0}")
        .andExpect(status().isNotFound());
    List<OutboxEvent> events =
        outboxEvents.findAll().stream().filter(e -> e.getPayload().contains(id)).toList();
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getType()).isEqualTo(OutboxEventType.DELETE_PROPERTY_FILES);
    assertThat(events.get(0).getStatus()).isEqualTo("PENDING");
    assertThat(events.get(0).getAttempts()).isZero();
  }

  @Test
  void shouldAppendRoomsAndKeepThemOrdered() throws Exception {
    String id = createProperty(owner, FLAT);
    createRoom(owner, id, "{\"name\":\"Кухня\",\"roomType\":\"KITCHEN\"}");
    createRoom(owner, id, "{\"name\":\"Ванная\",\"roomType\":\"BATHROOM\"}");
    createRoom(owner, id, "{\"name\":\" Прихожая \",\"roomType\":\"HALLWAY\",\"sortOrder\":5}");
    createRoom(owner, id, "{\"name\":\"Балкон\",\"roomType\":\"BALCONY\"}");

    mockMvc
        .perform(get(PROPERTIES + "/" + id + "/rooms").header("Authorization", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name", contains("Кухня", "Ванная", "Прихожая", "Балкон")))
        .andExpect(jsonPath("$[*].sortOrder", contains(0, 1, 5, 6)));
    mockMvc
        .perform(get(PROPERTIES + "/" + id).header("Authorization", owner))
        .andExpect(jsonPath("$.rooms.length()").value(4));
  }

  @Test
  void shouldUpdateAndDeleteRoom() throws Exception {
    String id = createProperty(owner, FLAT);
    String roomId = createRoom(owner, id, "{\"name\":\"Кухня\",\"roomType\":\"KITCHEN\"}");

    patchJson(owner, "/api/v1/rooms/" + roomId, "{\"sortOrder\":5,\"version\":0}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Кухня"))
        .andExpect(jsonPath("$.sortOrder").value(5))
        .andExpect(jsonPath("$.propertyId").value(id))
        .andExpect(jsonPath("$.version").value(1));
    patchJson(owner, "/api/v1/rooms/" + roomId, "{\"name\":\"Столовая\",\"version\":0}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    postJson(
            owner, PROPERTIES + "/" + id + "/rooms", "{\"name\":\"Кухня\",\"roomType\":\"GARAGE\"}")
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(delete("/api/v1/rooms/" + roomId).header("Authorization", owner))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get(PROPERTIES + "/" + id + "/rooms").header("Authorization", owner))
        .andExpect(jsonPath("$").isEmpty());
  }

  private String createProperty(String bearer, String body) throws Exception {
    return idOf(postJson(bearer, PROPERTIES, body).andExpect(status().isCreated()).andReturn());
  }

  private String createRoom(String bearer, String propertyId, String body) throws Exception {
    MvcResult result =
        postJson(bearer, PROPERTIES + "/" + propertyId + "/rooms", body)
            .andExpect(status().isCreated())
            .andReturn();
    return idOf(result);
  }

  private ResultActions postJson(String bearer, String path, String body) throws Exception {
    return mockMvc.perform(
        post(path)
            .header("Authorization", bearer)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private ResultActions patchJson(String bearer, String path, String body) throws Exception {
    return mockMvc.perform(
        patch(path)
            .header("Authorization", bearer)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private static String idOf(MvcResult result) throws UnsupportedEncodingException {
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }
}
