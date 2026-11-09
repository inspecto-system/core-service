package dev.inspecto.core.suggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.inspecto.core.TestcontainersConfiguration;
import dev.inspecto.core.support.ApiUsers;
import dev.inspecto.core.support.FakeMedia;
import dev.inspecto.core.support.FakeMl;
import dev.inspecto.core.support.Fixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Category suggestions through ml-service and the user's decision (T-602). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CategorySuggestionIntegrationTest {

  private static final FakeMedia MEDIA = FakeMedia.start();
  private static final FakeMl ML = FakeMl.start();
  private static final String THREE =
      "{\"code\":\"CRACK\",\"probability\":0.81},"
          + "{\"code\":\"PEELING\",\"probability\":0.1},"
          + "{\"code\":\"UNKNOWN\",\"probability\":0.05}";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;

  private Fixtures api;
  private UUID owner;
  private UUID propertyId;

  @DynamicPropertySource
  static void services(DynamicPropertyRegistry registry) {
    registry.add("inspecto.media.url", MEDIA::url);
    registry.add("inspecto.ml.url", ML::url);
    registry.add("inspecto.ml.timeout", () -> "500ms");
  }

  @AfterAll
  static void stop() {
    MEDIA.close();
    ML.close();
  }

  @BeforeEach
  void owner() throws Exception {
    MEDIA.failWith(0);
    ML.answer("model-1", THREE);
    ML.clearCalls();
    api = new Fixtures(mockMvc, ApiUsers.newInspector(mockMvc));
    owner = UUID.fromString(JsonPath.read(api.read("/api/v1/users/me"), "$.id"));
    propertyId = UUID.fromString(api.property("Квартира"));
  }

  @Test
  void shouldSuggestKnownSystemCategoriesInRequestLanguageAndRemember() throws Exception {
    UUID photo = MEDIA.upload(owner, propertyId);

    String body =
        suggest(photo, "ru")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modelVersion").value("model-1"))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].code").value("CRACK"))
            .andExpect(jsonPath("$.items[0].name").value("Трещина"))
            .andExpect(jsonPath("$.items[0].probability").value(0.81))
            .andExpect(jsonPath("$.items[1].code").value("PEELING"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String suggestionId = JsonPath.read(body, "$.suggestionId");
    String predictions =
        jdbc.queryForObject(
            "select predictions::text from ml_suggestions where id = ?::uuid",
            String.class,
            suggestionId);
    assertThat(predictions).contains("CRACK", "PEELING").doesNotContain("UNKNOWN");
    assertThat(ML.calls())
        .singleElement()
        .satisfies(call -> {
          assertThat(call.tokenClaims()).contains("\"sub\":\"core-service\"", "ml.classify");
          assertThat(call.path()).isEqualTo("/internal/v1/classify");
        });
  }

  @Test
  void shouldNotClassifySomeoneElsesOrUnknownPhoto() throws Exception {
    UUID foreign = MEDIA.upload(UUID.randomUUID(), propertyId);

    suggest(foreign, "ru").andExpect(status().isNotFound());
    suggest(UUID.randomUUID(), "ru").andExpect(status().isNotFound());
    assertThat(ML.calls()).isEmpty();
  }

  @Test
  void shouldAnswerWithEmptyListWhenMlFailsIsSlowOrHasNothing() throws Exception {
    UUID photo = MEDIA.upload(owner, propertyId);

    ML.failWith(503);
    expectEmpty(suggest(photo, "ru"));
    ML.answer("model-1", THREE);
    ML.delay(1500);
    expectEmpty(suggest(photo, "ru"));
    ML.answer("model-1", "");
    expectEmpty(suggest(photo, "ru"));
  }

  @Test
  void shouldAnswerWithEmptyListWhenMediaIsUnavailable() throws Exception {
    UUID photo = MEDIA.upload(owner, propertyId);
    MEDIA.failWith(503);

    expectEmpty(suggest(photo, "ru"));
    assertThat(ML.calls()).isEmpty();
  }

  @Test
  void shouldRecordWhichCategoryTheUserChose() throws Exception {
    Scene scene = scene();

    String top = createDefect(scene, api.systemCategory(Fixtures.CRACK), suggestionFor());
    String other = createDefect(scene, api.systemCategory("PEELING"), suggestionFor());
    String rejected = createDefect(scene, api.ownCategory("Своя"), suggestionFor());

    assertThat(decisionOf(top)).isEqualTo("ACCEPTED_TOP1");
    assertThat(decisionOf(other)).isEqualTo("ACCEPTED_OTHER");
    assertThat(decisionOf(rejected)).isEqualTo("REJECTED");
  }

  @Test
  void shouldRejectUsedOrForeignSuggestion() throws Exception {
    Scene scene = scene();
    String suggestionId = suggestionFor();
    String crack = api.systemCategory(Fixtures.CRACK);
    String first = createDefect(scene, crack, suggestionId);
    Fixtures stranger = new Fixtures(mockMvc, ApiUsers.newInspector(mockMvc));

    expectUnknownSuggestion(api, scene, crack, suggestionId); // already used
    expectUnknownSuggestion(stranger, sceneOf(stranger), crack, suggestionId); // someone else's

    assertThat(decisionOf(first)).isEqualTo("ACCEPTED_TOP1"); // the first decision is kept
  }

  private static void expectUnknownSuggestion(
      Fixtures user, Scene scene, String categoryId, String suggestionId) throws Exception {
    user.send(
            post("/api/v1/inspections/" + scene.inspectionId() + "/defects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(withSuggestion(scene.roomId(), categoryId, suggestionId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("suggestionId"))
        .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_ID"));
  }

  private record Scene(String inspectionId, String roomId) {}

  private Scene scene() throws Exception {
    return sceneOf(api);
  }

  private static Scene sceneOf(Fixtures user) throws Exception {
    String property = user.property("Дом");
    String room = user.room(property, "Кухня", 0);
    return new Scene(user.startedInspection(property, "2026-11-09T09:00:00Z", List.of(room)), room);
  }

  private String suggestionFor() throws Exception {
    UUID photo = MEDIA.upload(owner, propertyId);
    String body = suggest(photo, "ru").andReturn().getResponse().getContentAsString();
    return JsonPath.read(body, "$.suggestionId");
  }

  private String createDefect(Scene scene, String categoryId, String suggestionId)
      throws Exception {
    return Fixtures.idOf(
        api.created(
            "/api/v1/inspections/" + scene.inspectionId() + "/defects",
            withSuggestion(scene.roomId(), categoryId, suggestionId)));
  }

  private static String withSuggestion(String roomId, String categoryId, String suggestionId) {
    String body = Fixtures.defectBody(roomId, categoryId, "Дефект");
    return body.substring(0, body.length() - 1) + ",\"suggestionId\":\"" + suggestionId + "\"}";
  }

  private String decisionOf(String defectId) {
    return jdbc.queryForObject(
        "select decision from ml_suggestions where defect_id = ?::uuid", String.class, defectId);
  }

  private ResultActions suggest(UUID photoId, String language) throws Exception {
    return api.send(
        post("/api/v1/category-suggestions")
            .header("Accept-Language", language)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"photoId\":\"" + photoId + "\"}"));
  }

  private static void expectEmpty(ResultActions result) throws Exception {
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.suggestionId").doesNotExist());
  }
}
