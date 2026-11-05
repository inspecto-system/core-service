package dev.inspecto.core.inspection;

import static dev.inspecto.core.support.Fixtures.idOf;
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
import dev.inspecto.core.support.ApiUsers;
import dev.inspecto.core.support.Fixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Defects, observations and status history (T-203, ADR-003, ADR-004). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DefectApiIntegrationTest {

  private static final String OCTOBER = "2026-10-01T09:00:00Z";
  private static final String NOVEMBER = "2026-11-01T09:00:00Z";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;

  private Fixtures api;
  private String propertyId;
  private String kitchenId;
  private String hallId;
  private String crackId;
  private String first;

  @BeforeEach
  void createPropertyAndFirstInspection() throws Exception {
    api = new Fixtures(mockMvc, ApiUsers.newInspector(mockMvc));
    propertyId = api.property("Квартира");
    kitchenId = api.room(propertyId, "Кухня", 0);
    hallId = api.room(propertyId, "Прихожая", 1);
    crackId = api.systemCategory(Fixtures.CRACK);
    first = api.startedInspection(propertyId, OCTOBER, List.of(kitchenId, hallId));
  }

  @Test
  void shouldRegisterDefectWithFirstObservationAndHistory() throws Exception {
    String inspection = api.read(inspectionPath(first));
    String wallsItem = JsonPath.read(inspection, "$.rooms[0].checklist[0].id");
    String body =
        "{\"roomId\":\""
            + kitchenId
            + "\",\"checklistItemId\":\""
            + wallsItem
            + "\",\"categoryId\":\""
            + crackId
            + "\",\"elementGroup\":\"WALLS\",\"title\":\" Трещина у окна \","
            + "\"description\":\"Длина 30 см\",\"severity\":\"MAJOR\",\"comment\":\"Свежая\"}";

    String defect =
        api.send(post(inspectionPath(first) + "/defects").content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Трещина у окна"))
            .andExpect(jsonPath("$.currentStatus").value("OPEN"))
            .andExpect(jsonPath("$.firstInspectionId").value(first))
            .andExpect(jsonPath("$.propertyId").value(propertyId))
            .andExpect(jsonPath("$.categoryId").value(crackId))
            .andExpect(jsonPath("$.observations.length()").value(1))
            .andExpect(jsonPath("$.observations[0].status").value("OPEN"))
            .andExpect(jsonPath("$.observations[0].severity").value("MAJOR"))
            .andExpect(jsonPath("$.observations[0].comment").value("Свежая"))
            .andExpect(jsonPath("$.observations[0].checklistItemId").value(wallsItem))
            .andExpect(jsonPath("$.observations[0].photos").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String defectId = idOf(defect);

    api.send(get(inspectionPath(first)))
        .andExpect(jsonPath("$.rooms[0].observations.length()").value(1))
        .andExpect(jsonPath("$.rooms[0].observations[0].defect.id").value(defectId))
        .andExpect(jsonPath("$.rooms[1].observations").isEmpty());
    api.send(get("/api/v1/defects/" + defectId + "/history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].fromStatus").value(nullValue()))
        .andExpect(jsonPath("$[0].toStatus").value("OPEN"))
        .andExpect(jsonPath("$[0].inspectionId").value(first))
        .andExpect(jsonPath("$[0].comment").value("Свежая"));
    api.send(get("/api/v1/properties/" + propertyId))
        .andExpect(jsonPath("$.openDefectCount").value(1));
    api.send(get(propertyPath() + "/inspections"))
        .andExpect(jsonPath("$.items[0].defectCount").value(1))
        .andExpect(jsonPath("$.items[0].newDefectCount").value(1));
  }

  @Test
  void shouldRejectUnknownReferences() throws Exception {
    String otherProperty = api.property("Дача");
    String foreignRoom = api.room(otherProperty, "Веранда", 0);
    String hallItem = JsonPath.read(api.read(inspectionPath(first)), "$.rooms[1].checklist[0].id");
    String foreignCategory =
        new Fixtures(mockMvc, ApiUsers.newInspector(mockMvc)).ownCategory("Чужая");
    String inactive = api.ownCategory("Отключённая");
    api.send(patch("/api/v1/defect-categories/" + inactive).content("{\"active\":false}"))
        .andExpect(status().isOk());

    assertInvalid(
        postDefect(Fixtures.defectBody(foreignRoom, crackId, "x")), "roomId", "UNKNOWN_ID");
    assertInvalid(
        postDefect(Fixtures.defectBody(kitchenId, foreignCategory, "x")),
        "categoryId",
        "UNKNOWN_ID");
    assertInvalid(
        postDefect(Fixtures.defectBody(kitchenId, inactive, "x")), "categoryId", "INACTIVE");
    String wrongItem =
        Fixtures.defectBody(kitchenId, crackId, "x")
            .replace("{", "{\"checklistItemId\":\"" + hallItem + "\",");
    assertInvalid(postDefect(wrongItem), "checklistItemId", "UNKNOWN_ID");
    postDefect(Fixtures.defectBody(kitchenId, crackId, " "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("title"));
  }

  @Test
  void shouldResolveOnlyThroughExplicitObservationOfRepeatInspection() throws Exception {
    String defectId = idOf(api.defect(first, kitchenId, crackId, "Трещина"));
    String repeat = api.startedInspection(propertyId, NOVEMBER, List.of(kitchenId));

    api.send(get(inspectionPath(repeat) + "/open-defects"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].defect.id").value(defectId))
        .andExpect(jsonPath("$[0].lastObservation.inspectionId").value(first))
        .andExpect(jsonPath("$[0].currentObservation").value(nullValue()));
    api.send(get(inspectionPath(first) + "/open-defects")).andExpect(jsonPath("$").isEmpty());

    api.send(
            post(inspectionPath(repeat) + "/observations")
                .content(
                    "{\"defectId\":\""
                        + defectId
                        + "\",\"status\":\"RESOLVED\",\"comment\":\"Заделана\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("RESOLVED"))
        .andExpect(jsonPath("$.severity").value("MAJOR"))
        .andExpect(jsonPath("$.inspectionId").value(repeat));

    api.send(get("/api/v1/defects/" + defectId))
        .andExpect(jsonPath("$.currentStatus").value("RESOLVED"))
        .andExpect(jsonPath("$.observations[*].inspectionId", contains(repeat, first)));
    api.send(get("/api/v1/defects/" + defectId + "/history"))
        .andExpect(jsonPath("$[*].toStatus", contains("OPEN", "RESOLVED")))
        .andExpect(jsonPath("$[1].fromStatus").value("OPEN"))
        .andExpect(jsonPath("$[1].inspectionId").value(repeat))
        .andExpect(jsonPath("$[1].comment").value("Заделана"));
    api.send(get(inspectionPath(repeat) + "/open-defects"))
        .andExpect(jsonPath("$[0].currentObservation.status").value("RESOLVED"));
    api.send(get("/api/v1/properties/" + propertyId))
        .andExpect(jsonPath("$.openDefectCount").value(0));

    api.send(
            post(inspectionPath(repeat) + "/observations")
                .content("{\"defectId\":\"" + defectId + "\",\"status\":\"OPEN\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_OBSERVED"));
    String later = api.startedInspection(propertyId, "2026-12-01T09:00:00Z", List.of(kitchenId));
    api.send(get(inspectionPath(later) + "/open-defects")).andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void shouldTakeStatusFromLatestInspectionByDate() throws Exception {
    String defectId = idOf(api.defect(first, kitchenId, crackId, "Трещина"));
    String november = api.startedInspection(propertyId, NOVEMBER, List.of(kitchenId));
    api.observation(november, defectId, "RESOLVED");
    String september =
        api.startedInspection(propertyId, "2026-09-15T09:00:00Z", List.of(kitchenId));

    api.observation(september, defectId, "OPEN");
    api.send(get("/api/v1/defects/" + defectId))
        .andExpect(jsonPath("$.currentStatus").value("RESOLVED"))
        .andExpect(
            jsonPath("$.observations[*].inspectionId", contains(november, first, september)));

    api.send(
            patch(inspectionPath(november))
                .content("{\"inspectedAt\":\"2026-09-01T09:00:00Z\",\"version\":1}"))
        .andExpect(status().isOk());
    api.send(get("/api/v1/defects/" + defectId))
        .andExpect(jsonPath("$.currentStatus").value("OPEN"));
    api.send(get("/api/v1/defects/" + defectId + "/history"))
        .andExpect(jsonPath("$[*].toStatus", contains("OPEN", "RESOLVED", "OPEN")))
        .andExpect(jsonPath("$[2].inspectionId").value(november));
  }

  @Test
  void shouldChangeObservationAndRecomputeStatus() throws Exception {
    String defectId = idOf(api.defect(first, kitchenId, crackId, "Трещина"));
    String repeat = api.startedInspection(propertyId, NOVEMBER, List.of(kitchenId));
    String observation =
        idOf(
            api.created(
                inspectionPath(repeat) + "/observations",
                "{\"defectId\":\""
                    + defectId
                    + "\",\"status\":\"OPEN\",\"severity\":\"CRITICAL\",\"comment\":\"Растёт\"}"));

    api.send(patch(observationPath(observation)).content("{\"status\":\"RESOLVED\",\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"))
        .andExpect(jsonPath("$.severity").value("CRITICAL"))
        .andExpect(jsonPath("$.comment").value("Растёт"))
        .andExpect(jsonPath("$.version").value(1));
    api.send(get("/api/v1/defects/" + defectId))
        .andExpect(jsonPath("$.currentStatus").value("RESOLVED"));

    api.send(patch(observationPath(observation)).content("{\"comment\":null,\"version\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comment").value(nullValue()))
        .andExpect(jsonPath("$.status").value("RESOLVED"));
    api.send(patch(observationPath(observation)).content("{\"status\":\"OPEN\",\"version\":0}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    api.send(get("/api/v1/defects/" + defectId + "/history"))
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void shouldFreezeDefectsOfCompletedInspection() throws Exception {
    String defect = api.defect(first, kitchenId, crackId, "Трещина");
    String observation = JsonPath.read(defect, "$.observations[0].id");
    api.send(post(inspectionPath(first) + "/complete").content("{\"version\":1}"))
        .andExpect(status().isOk());

    assertFrozen(postDefect(Fixtures.defectBody(kitchenId, crackId, "Ещё")));
    assertFrozen(
        api.send(
            patch(observationPath(observation))
                .content("{\"status\":\"RESOLVED\",\"version\":0}")));
    assertFrozen(
        api.send(
            post(inspectionPath(first) + "/observations")
                .content("{\"defectId\":\"" + idOf(defect) + "\",\"status\":\"OPEN\"}")));
  }

  @Test
  void shouldUpdateDefectFields() throws Exception {
    String defectId = idOf(api.defect(first, kitchenId, crackId, "Трещина"));
    String own = api.ownCategory("Сколы плитки");
    String change =
        "{\"title\":\"Скол\",\"categoryId\":\""
            + own
            + "\",\"description\":\"У ванны\",\"severity\":\"MINOR\",\"version\":0}";

    api.send(patch(defectPath(defectId)).content(change))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Скол"))
        .andExpect(jsonPath("$.categoryId").value(own))
        .andExpect(jsonPath("$.description").value("У ванны"))
        .andExpect(jsonPath("$.severity").value("MINOR"))
        .andExpect(jsonPath("$.elementGroup").value("WALLS"))
        .andExpect(jsonPath("$.version").value(1));
    api.send(patch(defectPath(defectId)).content("{\"description\":null,\"version\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value(nullValue()))
        .andExpect(jsonPath("$.title").value("Скол"));
    api.send(patch(defectPath(defectId)).content("{\"title\":\"x\",\"version\":0}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    api.send(patch(defectPath(defectId)).content("{\"title\":\" \",\"version\":2}"))
        .andExpect(status().isBadRequest());
    String foreign = new Fixtures(mockMvc, ApiUsers.newInspector(mockMvc)).ownCategory("Чужая");
    assertInvalid(
        api.send(
            patch(defectPath(defectId))
                .content("{\"categoryId\":\"" + foreign + "\",\"version\":2}")),
        "categoryId",
        "UNKNOWN_ID");
  }

  @Test
  void shouldListDefectsWithFilters() throws Exception {
    String crack = idOf(api.defect(first, kitchenId, crackId, "Трещина"));
    api.created(
        inspectionPath(first) + "/defects",
        Fixtures.defectBody(hallId, crackId, "Пятно").replace("MAJOR", "COSMETIC"));
    String repeat = api.startedInspection(propertyId, NOVEMBER, List.of(kitchenId));
    api.observation(repeat, crack, "RESOLVED");

    api.send(get(propertyPath() + "/defects"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items[*].title", contains("Пятно", "Трещина")));
    api.send(get(propertyPath() + "/defects").param("status", "OPEN"))
        .andExpect(jsonPath("$.items[*].title", contains("Пятно")));
    api.send(get(propertyPath() + "/defects").param("roomId", kitchenId))
        .andExpect(jsonPath("$.items[*].title", contains("Трещина")));
    api.send(get(propertyPath() + "/defects").param("severity", "COSMETIC"))
        .andExpect(jsonPath("$.items[*].title", contains("Пятно")));
    api.send(get(propertyPath() + "/defects").param("status", "BROKEN"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldDeleteDraftWithDefectsFoundOnlyThere() throws Exception {
    String draft = idOf(api.inspection(propertyId, NOVEMBER, List.of(kitchenId)));
    String defectId = idOf(api.defect(draft, kitchenId, crackId, "Трещина"));

    api.send(delete(inspectionPath(draft))).andExpect(status().isNoContent());

    api.send(get(defectPath(defectId))).andExpect(status().isNotFound());
    Integer history =
        jdbc.queryForObject(
            "select count(*) from defect_status_history where defect_id = ?::uuid",
            Integer.class,
            defectId);
    assertThat(history).isZero();
  }

  @Test
  void shouldKeepDraftThatSharesDefectsWithOtherInspections() throws Exception {
    String older = idOf(api.defect(first, kitchenId, crackId, "Трещина"));
    String observingDraft = idOf(api.inspection(propertyId, NOVEMBER, List.of(kitchenId)));
    api.observation(observingDraft, older, "RESOLVED");
    String findingDraft = idOf(api.inspection(propertyId, "2026-09-01T09:00:00Z", List.of(hallId)));
    String found = idOf(api.defect(findingDraft, hallId, crackId, "Пятно"));
    api.observation(first, found, "OPEN");

    assertInUse(api.send(delete(inspectionPath(observingDraft))));
    assertInUse(api.send(delete(inspectionPath(findingDraft))));
    api.send(get(defectPath(older))).andExpect(jsonPath("$.currentStatus").value("RESOLVED"));
  }

  @Test
  void shouldNotDeleteRoomOrCategoryWithDefects() throws Exception {
    String bathroom = api.room(propertyId, "Ванная", 2);
    String own = api.ownCategory("Затирка");
    String unused = api.ownCategory("Не нужна");
    api.defect(first, bathroom, own, "Тёмная затирка");

    api.send(get(inspectionPath(first)))
        .andExpect(
            jsonPath("$.rooms[*].room.name", containsInAnyOrder("Кухня", "Прихожая", "Ванная")))
        .andExpect(jsonPath("$.rooms[2].checklist").isEmpty())
        .andExpect(jsonPath("$.rooms[2].observations[0].defect.title").value("Тёмная затирка"));
    assertInUse(api.send(delete("/api/v1/rooms/" + bathroom)));
    assertInUse(api.send(delete("/api/v1/defect-categories/" + own)));
    api.send(delete("/api/v1/defect-categories/" + unused)).andExpect(status().isNoContent());
  }

  @Test
  void shouldRejectObservationOfAnotherPropertyOrRoom() throws Exception {
    String otherProperty = api.property("Дача");
    String otherRoom = api.room(otherProperty, "Веранда", 0);
    String otherInspection = api.startedInspection(otherProperty, OCTOBER, List.of(otherRoom));
    String foreignDefect = idOf(api.defect(otherInspection, otherRoom, crackId, "Чужой"));
    String own = idOf(api.defect(first, kitchenId, crackId, "Трещина"));
    String repeat = api.startedInspection(propertyId, NOVEMBER, List.of(kitchenId, hallId));
    String hallItem = JsonPath.read(api.read(inspectionPath(repeat)), "$.rooms[1].checklist[0].id");

    assertInvalid(
        api.send(
            post(inspectionPath(repeat) + "/observations")
                .content("{\"defectId\":\"" + foreignDefect + "\",\"status\":\"OPEN\"}")),
        "defectId",
        "UNKNOWN_ID");
    assertInvalid(
        api.send(
            post(inspectionPath(repeat) + "/observations")
                .content(
                    "{\"defectId\":\""
                        + own
                        + "\",\"status\":\"OPEN\",\"checklistItemId\":\""
                        + hallItem
                        + "\"}")),
        "checklistItemId",
        "UNKNOWN_ID");
  }

  @Test
  void shouldDeletePropertyWithDefects() throws Exception {
    String defectId = idOf(api.defect(first, kitchenId, crackId, "Трещина"));
    String repeat = api.startedInspection(propertyId, NOVEMBER, List.of(kitchenId));
    api.observation(repeat, defectId, "RESOLVED");

    api.send(delete(propertyPath())).andExpect(status().isAccepted());

    Integer defects =
        jdbc.queryForObject(
            "select count(*) from defects where property_id = ?::uuid", Integer.class, propertyId);
    assertThat(defects).isZero();
  }

  private ResultActions postDefect(String body) throws Exception {
    return api.send(post(inspectionPath(first) + "/defects").content(body));
  }

  private static void assertInvalid(ResultActions result, String field, String code)
      throws Exception {
    result
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value(field))
        .andExpect(jsonPath("$.errors[0].code").value(code));
  }

  private static void assertFrozen(ResultActions result) throws Exception {
    result
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
  }

  private static void assertInUse(ResultActions result) throws Exception {
    result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
  }

  private String propertyPath() {
    return "/api/v1/properties/" + propertyId;
  }

  private static String inspectionPath(String id) {
    return "/api/v1/inspections/" + id;
  }

  private static String defectPath(String id) {
    return "/api/v1/defects/" + id;
  }

  private static String observationPath(String id) {
    return "/api/v1/observations/" + id;
  }
}
