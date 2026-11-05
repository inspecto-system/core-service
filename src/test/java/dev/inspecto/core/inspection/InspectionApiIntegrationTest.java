package dev.inspecto.core.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
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
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Inspections and checklists (T-201, T-202). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InspectionApiIntegrationTest {

  private static final String TEMPLATE = "ROOM_STANDARD";
  private static final int TEMPLATE_ITEMS = 8;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;

  private String owner;
  private String propertyId;
  private String kitchenId;
  private String hallId;

  @BeforeEach
  void createPropertyWithRooms() throws Exception {
    owner = ApiUsers.newInspector(mockMvc);
    propertyId =
        idOf(
            created(
                "/api/v1/properties",
                "{\"name\":\"Квартира\",\"type\":\"APARTMENT\",\"address\":\"ул. Мира, 5\"}"));
    // The hall is created second but sorted first, so display order is checked as well.
    kitchenId = createRoom("Кухня", "KITCHEN", 1);
    hallId = createRoom("Прихожая", "HALLWAY", 0);
  }

  @Test
  void shouldCreateDraftWithChecklistForEveryRoom() throws Exception {
    String body =
        newInspection("  Приёмка  ", List.of(kitchenId, hallId), List.of(TEMPLATE, TEMPLATE));

    String json =
        send(post(inspectionsOfProperty()).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.title").value("Приёмка"))
            .andExpect(jsonPath("$.notes").value(nullValue()))
            .andExpect(jsonPath("$.completedAt").value(nullValue()))
            .andExpect(jsonPath("$.propertyId").value(propertyId))
            .andExpect(jsonPath("$.version").value(0))
            .andExpect(jsonPath("$.rooms[*].room.name", contains("Прихожая", "Кухня")))
            .andExpect(jsonPath("$.rooms[0].checklist.length()").value(TEMPLATE_ITEMS))
            .andExpect(jsonPath("$.rooms[1].checklist.length()").value(TEMPLATE_ITEMS))
            .andExpect(jsonPath("$.rooms[0].checklist[0].elementGroup").value("WALLS"))
            .andExpect(
                jsonPath("$.rooms[0].checklist[0].title")
                    .value("Стены: ровность, трещины, отделка"))
            .andExpect(
                jsonPath("$.rooms[0].checklist[*].result", everyItem(equalTo("NOT_CHECKED"))))
            .andExpect(jsonPath("$.rooms[0].checklist[*].custom", everyItem(equalTo(false))))
            .andExpect(jsonPath("$.rooms[0].observations").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Integer> sortOrders = JsonPath.read(json, "$.rooms[1].checklist[*].sortOrder");
    assertThat(sortOrders).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
    String inspectorId = JsonPath.read(json, "$.inspectorId");
    assertThat(inspectorId).isNotBlank();

    mockMvc
        .perform(get("/api/v1/inspections/" + idOf(json)).header("Authorization", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rooms[*].room.id", contains(hallId, kitchenId)))
        .andExpect(jsonPath("$.rooms[1].checklist.length()").value(TEMPLATE_ITEMS));
  }

  @Test
  void shouldCopyTitlesInRequestedLanguage() throws Exception {
    send(post(inspectionsOfProperty())
            .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
            .content(newInspection(null, List.of(kitchenId), List.of(TEMPLATE))))
        .andExpect(status().isCreated())
        .andExpect(
            jsonPath("$.rooms[0].checklist[0].title").value("Walls: evenness, cracks, finish"));
  }

  @Test
  void shouldRejectRoomOfAnotherProperty() throws Exception {
    String otherProperty =
        idOf(
            created(
                "/api/v1/properties",
                "{\"name\":\"Дача\",\"type\":\"HOUSE\",\"address\":\"СНТ Берёзка\"}"));
    String foreignRoom =
        idOf(
            created(
                "/api/v1/properties/" + otherProperty + "/rooms",
                "{\"name\":\"Веранда\",\"roomType\":\"OTHER\"}"));

    send(post(inspectionsOfProperty())
            .content(newInspection(null, List.of(kitchenId, foreignRoom), List.of(TEMPLATE))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("roomIds"))
        .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_ID"));
    assertThat(inspectionRows()).isZero();
  }

  @Test
  void shouldRejectUnknownTemplate() throws Exception {
    send(post(inspectionsOfProperty())
            .content(newInspection(null, List.of(kitchenId), List.of(TEMPLATE, "NO_SUCH"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("templateCodes"))
        .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_CODE"));
  }

  @Test
  void shouldRejectInspectionWithoutRoomsOrDate() throws Exception {
    String body = "{\"roomIds\":[],\"templateCodes\":[\"" + TEMPLATE + "\"]}";
    send(post(inspectionsOfProperty()).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[*].field", hasItem("roomIds")))
        .andExpect(jsonPath("$.errors[*].field", hasItem("inspectedAt")));
  }

  @Test
  void shouldListNewestFirstAndCountOnPropertyCard() throws Exception {
    createInspection("2026-10-01T09:00:00Z");
    createInspection("2026-11-03T09:00:00Z");
    createInspection("2026-09-15T09:00:00Z");

    mockMvc
        .perform(get(inspectionsOfProperty()).header("Authorization", owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(3))
        .andExpect(
            jsonPath(
                "$.items[*].inspectedAt",
                contains("2026-11-03T09:00:00Z", "2026-10-01T09:00:00Z", "2026-09-15T09:00:00Z")))
        .andExpect(jsonPath("$.items[0].defectCount").value(0))
        .andExpect(jsonPath("$.items[0].newDefectCount").value(0));
    mockMvc
        .perform(
            get(inspectionsOfProperty())
                .param("size", "1")
                .param("page", "2")
                .header("Authorization", owner))
        .andExpect(jsonPath("$.items[0].inspectedAt").value("2026-09-15T09:00:00Z"));
    mockMvc
        .perform(get("/api/v1/properties/" + propertyId).header("Authorization", owner))
        .andExpect(jsonPath("$.inspectionCount").value(3))
        .andExpect(jsonPath("$.lastInspectionAt").value("2026-11-03T09:00:00Z"))
        .andExpect(jsonPath("$.openDefectCount").value(0));
  }

  @Test
  void shouldShowNoLastInspectionWithoutInspections() throws Exception {
    mockMvc
        .perform(get("/api/v1/properties/" + propertyId).header("Authorization", owner))
        .andExpect(jsonPath("$.inspectionCount").value(0))
        .andExpect(jsonPath("$.lastInspectionAt").value(nullValue()));
  }

  @Test
  void shouldRejectOutOfRangePaging() throws Exception {
    mockMvc
        .perform(get(inspectionsOfProperty()).param("size", "101").header("Authorization", owner))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void shouldUpdateFieldsClearNotesAndStart() throws Exception {
    String id = createInspection("2026-11-05T09:00:00Z");
    String change =
        "{\"title\":\"Повторная приёмка\",\"notes\":\"Ключи у консьержа\","
            + "\"inspectedAt\":\"2026-11-06T08:30:00Z\",\"version\":0}";
    send(patch(inspection(id)).content(change))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Повторная приёмка"))
        .andExpect(jsonPath("$.notes").value("Ключи у консьержа"))
        .andExpect(jsonPath("$.inspectedAt").value("2026-11-06T08:30:00Z"))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.version").value(1));

    send(patch(inspection(id)).content("{\"notes\":null,\"status\":\"IN_PROGRESS\",\"version\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notes").value(nullValue()))
        .andExpect(jsonPath("$.title").value("Повторная приёмка"))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

    send(patch(inspection(id)).content("{\"status\":\"IN_PROGRESS\",\"version\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
  }

  @Test
  void shouldRejectStatusOtherThanInProgressAndStaleVersion() throws Exception {
    String id = createInspection("2026-11-05T09:00:00Z");

    send(patch(inspection(id)).content("{\"status\":\"COMPLETED\",\"version\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("status"));
    send(patch(inspection(id)).content("{\"title\":\"x\",\"version\":7}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    send(patch(inspection(id)).content("{\"title\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("version"));
  }

  @Test
  void shouldCompleteOnlyStartedInspectionAndFreezeIt() throws Exception {
    String json = createInspectionJson("2026-11-05T09:00:00Z");
    String id = idOf(json);
    String itemId = JsonPath.read(json, "$.rooms[0].checklist[0].id");

    send(post(inspection(id) + "/complete").content("{\"version\":0}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    send(patch(inspection(id)).content("{\"status\":\"IN_PROGRESS\",\"version\":0}"))
        .andExpect(status().isOk());
    send(post(inspection(id) + "/complete").content("{\"version\":0}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    send(post(inspection(id) + "/complete").content("{\"version\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.completedAt").isNotEmpty())
        .andExpect(jsonPath("$.version").value(2));

    expectFrozen(send(patch(inspection(id)).content("{\"title\":\"x\",\"version\":2}")));
    expectFrozen(send(post(inspection(id) + "/complete").content("{\"version\":2}")));
    expectFrozen(send(delete(inspection(id))));
    expectFrozen(send(patch(item(itemId)).content("{\"result\":\"OK\",\"version\":0}")));
    expectFrozen(
        send(
            post(inspection(id) + "/checklist-items")
                .content(customItem(kitchenId, "Кондиционер"))));
  }

  @Test
  void shouldDeleteOnlyDraftTogetherWithChecklist() throws Exception {
    String draft = createInspection("2026-11-05T09:00:00Z");
    String started = createInspection("2026-11-04T09:00:00Z");
    send(patch(inspection(started)).content("{\"status\":\"IN_PROGRESS\",\"version\":0}"))
        .andExpect(status().isOk());

    send(delete(inspection(started)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    send(delete(inspection(draft))).andExpect(status().isNoContent());

    mockMvc
        .perform(get(inspection(draft)).header("Authorization", owner))
        .andExpect(status().isNotFound());
    Integer items =
        jdbc.queryForObject(
            "select count(*) from inspection_checklist_items where inspection_id = ?::uuid",
            Integer.class,
            draft);
    assertThat(items).isZero();
  }

  @Test
  void shouldRecordResultAndClearComment() throws Exception {
    String json = createInspectionJson("2026-11-05T09:00:00Z");
    String itemId = JsonPath.read(json, "$.rooms[0].checklist[2].id");

    send(patch(item(itemId))
            .content("{\"result\":\"DEFECT\",\"comment\":\"  Скрипит ламинат \",\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result").value("DEFECT"))
        .andExpect(jsonPath("$.comment").value("Скрипит ламинат"))
        .andExpect(jsonPath("$.elementGroup").value("FLOOR"))
        .andExpect(jsonPath("$.version").value(1));
    send(patch(item(itemId)).content("{\"comment\":null,\"version\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result").value("DEFECT"))
        .andExpect(jsonPath("$.comment").value(nullValue()));
    send(patch(item(itemId)).content("{\"result\":\"OK\",\"version\":1}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    send(patch(item(itemId)).content("{\"result\":\"BROKEN\",\"version\":2}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void shouldAppendCustomItemToRoom() throws Exception {
    String id = createInspection("2026-11-05T09:00:00Z");

    send(post(inspection(id) + "/checklist-items").content(customItem(kitchenId, " Вытяжка ")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Вытяжка"))
        .andExpect(jsonPath("$.custom").value(true))
        .andExpect(jsonPath("$.templateItemId").value(nullValue()))
        .andExpect(jsonPath("$.result").value("NOT_CHECKED"))
        .andExpect(jsonPath("$.sortOrder").value(TEMPLATE_ITEMS));
    send(post(inspection(id) + "/checklist-items").content(customItem(hallId, "Домофон")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sortOrder").value(TEMPLATE_ITEMS));

    mockMvc
        .perform(get(inspection(id)).header("Authorization", owner))
        .andExpect(jsonPath("$.rooms[1].checklist.length()").value(TEMPLATE_ITEMS + 1))
        .andExpect(jsonPath("$.rooms[1].checklist[8].title").value("Вытяжка"));
  }

  @Test
  void shouldAddRoomToInspectionWithCustomItem() throws Exception {
    String bathroom = createRoom("Ванная", "BATHROOM", 2);
    String id = createInspection("2026-11-05T09:00:00Z");

    send(post(inspection(id) + "/checklist-items").content(customItem(bathroom, "Вентиляция")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sortOrder").value(0));
    mockMvc
        .perform(get(inspection(id)).header("Authorization", owner))
        .andExpect(jsonPath("$.rooms[*].room.name", contains("Прихожая", "Кухня", "Ванная")));
  }

  @Test
  void shouldRejectCustomItemInRoomOfAnotherPropertyOrWithoutTitle() throws Exception {
    String id = createInspection("2026-11-05T09:00:00Z");
    String otherProperty =
        idOf(
            created(
                "/api/v1/properties",
                "{\"name\":\"Офис\",\"type\":\"COMMERCIAL\",\"address\":\"пр. Победы, 2\"}"));
    String foreignRoom =
        idOf(
            created(
                "/api/v1/properties/" + otherProperty + "/rooms",
                "{\"name\":\"Склад\",\"roomType\":\"OTHER\"}"));

    send(post(inspection(id) + "/checklist-items").content(customItem(foreignRoom, "Стеллаж")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("roomId"))
        .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_ID"));
    send(post(inspection(id) + "/checklist-items").content(customItem(kitchenId, " ")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("title"));
  }

  @Test
  void shouldNotDeleteRoomUsedByInspection() throws Exception {
    createInspection("2026-11-05T09:00:00Z");
    String unused = createRoom("Балкон", "BALCONY", 5);

    send(delete("/api/v1/rooms/" + kitchenId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
    send(delete("/api/v1/rooms/" + unused)).andExpect(status().isNoContent());
  }

  @Test
  void shouldDeletePropertyWithInspections() throws Exception {
    String id = createInspection("2026-11-05T09:00:00Z");
    send(patch(inspection(id)).content("{\"status\":\"IN_PROGRESS\",\"version\":0}"))
        .andExpect(status().isOk());

    send(delete("/api/v1/properties/" + propertyId)).andExpect(status().isAccepted());

    assertThat(inspectionRows()).isZero();
    mockMvc
        .perform(get(inspection(id)).header("Authorization", owner))
        .andExpect(status().isNotFound());
  }

  private static void expectFrozen(ResultActions result) throws Exception {
    result
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
  }

  private String createInspection(String inspectedAt) throws Exception {
    return idOf(createInspectionJson(inspectedAt));
  }

  private String createInspectionJson(String inspectedAt) throws Exception {
    String body =
        "{\"inspectedAt\":\""
            + inspectedAt
            + "\",\"roomIds\":[\""
            + kitchenId
            + "\",\""
            + hallId
            + "\"],\"templateCodes\":[\""
            + TEMPLATE
            + "\"]}";
    return created(inspectionsOfProperty(), body);
  }

  private static String newInspection(String title, List<String> roomIds, List<String> codes) {
    String titleJson = title == null ? "" : ",\"title\":\"" + title + "\"";
    return "{\"inspectedAt\":\"2026-11-05T09:00:00Z\""
        + titleJson
        + ",\"roomIds\":"
        + jsonArray(roomIds)
        + ",\"templateCodes\":"
        + jsonArray(codes)
        + "}";
  }

  private static String customItem(String roomId, String title) {
    return "{\"roomId\":\"" + roomId + "\",\"elementGroup\":\"OTHER\",\"title\":\"" + title + "\"}";
  }

  private static String jsonArray(List<String> values) {
    return values.stream()
        .map(value -> "\"" + value + "\"")
        .collect(Collectors.joining(",", "[", "]"));
  }

  private String createRoom(String name, String type, int sortOrder) throws Exception {
    String body =
        "{\"name\":\"" + name + "\",\"roomType\":\"" + type + "\",\"sortOrder\":" + sortOrder + "}";
    return idOf(created("/api/v1/properties/" + propertyId + "/rooms", body));
  }

  private int inspectionRows() {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from inspections where property_id = ?::uuid",
            Integer.class,
            propertyId);
    return count == null ? 0 : count;
  }

  private String inspectionsOfProperty() {
    return "/api/v1/properties/" + propertyId + "/inspections";
  }

  private static String inspection(String id) {
    return "/api/v1/inspections/" + id;
  }

  private static String item(String id) {
    return "/api/v1/checklist-items/" + id;
  }

  private ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(
        request.header("Authorization", owner).contentType(MediaType.APPLICATION_JSON));
  }

  private String created(String path, String body) throws Exception {
    return send(post(path).content(body))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static String idOf(String json) {
    return JsonPath.read(json, "$.id");
  }
}
