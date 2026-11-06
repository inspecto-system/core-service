package dev.inspecto.core.inspection;

import static dev.inspecto.core.support.Fixtures.idOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.inspecto.core.TestcontainersConfiguration;
import dev.inspecto.core.support.ApiUsers;
import dev.inspecto.core.support.Fixtures;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Comparison of inspections (T-301): four inspections Z &lt; A &lt; C &lt; B and a defect for every
 * row of the comparison table, compared as A against B.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ComparisonApiIntegrationTest {

  @Autowired private MockMvc mockMvc;

  private Fixtures api;
  private String kitchenId;
  private String crackId;
  private String base;
  private String target;
  private final Map<String, String> defects = new HashMap<>();

  @BeforeEach
  void createHistory() throws Exception {
    api = new Fixtures(mockMvc, ApiUsers.newInspector(mockMvc));
    String propertyId = api.property("Квартира");
    kitchenId = api.room(propertyId, "Кухня", 0);
    crackId = api.systemCategory(Fixtures.CRACK);
    String earlier = api.startedInspection(propertyId, "2026-09-01T09:00:00Z", List.of(kitchenId));
    base = api.startedInspection(propertyId, "2026-10-01T09:00:00Z", List.of(kitchenId));
    String between = api.startedInspection(propertyId, "2026-11-01T09:00:00Z", List.of(kitchenId));
    target = api.startedInspection(propertyId, "2026-12-01T09:00:00Z", List.of(kitchenId));

    found("remaining", base);
    api.observation(target, defects.get("remaining"), "OPEN");
    found("resolved", base);
    api.observation(target, defects.get("resolved"), "RESOLVED");
    found("not-checked", base);
    found("new", target);
    found("reopened", earlier);
    api.observation(base, defects.get("reopened"), "RESOLVED");
    api.observation(target, defects.get("reopened"), "OPEN");
    // Open before A, not looked at in A: still REMAINING, not NEW (ADR-004 clarification).
    found("open-before-a", earlier);
    api.observation(target, defects.get("open-before-a"), "OPEN");
    found("found-between", between);
    found("fixed-between", between);
    api.observation(target, defects.get("fixed-between"), "RESOLVED");
    found("fixed-long-ago", earlier);
    api.observation(base, defects.get("fixed-long-ago"), "RESOLVED");
  }

  @Test
  void shouldClassifyEveryDefectByItsStatusAsOfBothInspections() throws Exception {
    String body =
        api.send(get(comparePath(base, target)).header("Accept-Language", "en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.base.id").value(base))
            .andExpect(jsonPath("$.target.inspectedAt").value("2026-12-01T09:00:00Z"))
            .andExpect(jsonPath("$.summary.new").value(2))
            .andExpect(jsonPath("$.summary.resolved").value(2))
            .andExpect(jsonPath("$.summary.remaining").value(2))
            .andExpect(jsonPath("$.summary.notChecked").value(2))
            .andExpect(jsonPath("$.defects.length()").value(8))
            .andExpect(jsonPath("$.bySeverity.MAJOR.new").value(2))
            .andExpect(jsonPath("$.bySeverity.COSMETIC.remaining").value(0))
            .andExpect(jsonPath("$.byCategory.length()").value(1))
            .andExpect(jsonPath("$.byCategory[0].categoryId").value(crackId))
            .andExpect(jsonPath("$.byCategory[0].remaining").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(state(body, "remaining")).isEqualTo("REMAINING");
    assertThat(state(body, "open-before-a")).isEqualTo("REMAINING");
    assertThat(state(body, "resolved")).isEqualTo("RESOLVED");
    assertThat(state(body, "fixed-between")).isEqualTo("RESOLVED");
    assertThat(state(body, "not-checked")).isEqualTo("NOT_CHECKED");
    assertThat(state(body, "found-between")).isEqualTo("NOT_CHECKED");
    assertThat(state(body, "new")).isEqualTo("NEW");
    assertThat(state(body, "reopened")).isEqualTo("NEW");
    assertThat(field(body, "reopened", "reopened")).isEqualTo(true);
    assertThat(field(body, "new", "reopened")).isEqualTo(false);
    assertThat(field(body, "remaining", "roomName")).isEqualTo("Кухня");
    List<String> listed = JsonPath.read(body, "$.defects[*].defectId");
    assertThat(listed).doesNotContain(defects.get("fixed-long-ago"));
  }

  @Test
  void shouldRequireEarlierBaseOfTheSameProperty() throws Exception {
    api.send(get(comparePath(target, base)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("target"));
    api.send(get(comparePath(base, base))).andExpect(status().isBadRequest());

    String otherProperty = api.property("Дом");
    String otherRoom = api.room(otherProperty, "Кухня", 0);
    String other = api.startedInspection(otherProperty, "2026-12-15T09:00:00Z", List.of(otherRoom));
    api.send(get(comparePath(base, other))).andExpect(status().isBadRequest());
  }

  @Test
  void shouldHideInspectionsOfOtherUsers() throws Exception {
    Fixtures stranger = new Fixtures(mockMvc, ApiUsers.newInspector(mockMvc));

    stranger.send(get(comparePath(base, target))).andExpect(status().isNotFound());
    api.send(get("/api/v1/inspections/compare?base=" + base + "&target=not-a-uuid"))
        .andExpect(status().isBadRequest());
  }

  private void found(String name, String inspectionId) throws Exception {
    defects.put(name, idOf(api.defect(inspectionId, kitchenId, crackId, name)));
  }

  private Object field(String body, String defect, String field) {
    List<Object> values =
        JsonPath.read(body, "$.defects[?(@.defectId == '" + defects.get(defect) + "')]." + field);
    return values.getFirst();
  }

  private String state(String body, String defect) {
    return (String) field(body, defect, "state");
  }

  private static String comparePath(String a, String b) {
    return "/api/v1/inspections/compare?base=" + a + "&target=" + b;
  }
}
