package dev.inspecto.core.inspection;

import static dev.inspecto.core.support.Fixtures.idOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Defect statistics of a property (T-302). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class StatisticsApiIntegrationTest {

  @Autowired private MockMvc mockMvc;

  private Fixtures api;
  private String propertyId;
  private String hallId;
  private String crackId;
  private String first;
  private String second;

  @BeforeEach
  void createDefects() throws Exception {
    api = new Fixtures(mockMvc, ApiUsers.newInspector(mockMvc));
    propertyId = api.property("Квартира");
    String kitchenId = api.room(propertyId, "Кухня", 0);
    hallId = api.room(propertyId, "Прихожая", 1);
    crackId = api.systemCategory(Fixtures.CRACK);
    first = api.startedInspection(propertyId, "2026-10-01T09:00:00Z", List.of(kitchenId, hallId));
    second = api.startedInspection(propertyId, "2026-11-01T09:00:00Z", List.of(kitchenId, hallId));

    defect(first, kitchenId, "MAJOR");
    defect(first, kitchenId, "CRITICAL");
    String fixed = defect(first, hallId, "MINOR");
    api.observation(second, fixed, "RESOLVED");
    defect(second, hallId, "MAJOR");
  }

  @Test
  void shouldCountDefectsByStatusGroupAndInspection() throws Exception {
    api.send(get(statisticsPath(propertyId)).header("Accept-Language", "en"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDefects").value(4))
        .andExpect(jsonPath("$.openDefects").value(3))
        .andExpect(jsonPath("$.resolvedDefects").value(1))
        // Every severity is listed; open MAJOR defects first, CRITICAL before MINOR among equal
        // counts.
        .andExpect(jsonPath("$.bySeverity.length()").value(4))
        .andExpect(jsonPath("$.bySeverity[0].key").value("MAJOR"))
        .andExpect(jsonPath("$.bySeverity[0].label").value("Major"))
        .andExpect(jsonPath("$.bySeverity[0].count").value(2))
        .andExpect(jsonPath("$.bySeverity[1].key").value("CRITICAL"))
        .andExpect(jsonPath("$.bySeverity[3].count").value(0))
        .andExpect(jsonPath("$.byCategory.length()").value(1))
        .andExpect(jsonPath("$.byCategory[0].key").value(crackId))
        .andExpect(jsonPath("$.byCategory[0].count").value(3))
        .andExpect(jsonPath("$.byRoom[0].label").value("Кухня"))
        .andExpect(jsonPath("$.byRoom[0].count").value(2))
        .andExpect(jsonPath("$.byRoom[1].key").value(hallId))
        .andExpect(jsonPath("$.byRoom[1].count").value(1))
        .andExpect(jsonPath("$.byInspection.length()").value(2))
        .andExpect(jsonPath("$.byInspection[0].inspectionId").value(first))
        .andExpect(jsonPath("$.byInspection[0].status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.byInspection[0].openDefects").value(3))
        .andExpect(jsonPath("$.byInspection[1].inspectionId").value(second))
        .andExpect(jsonPath("$.byInspection[1].openDefects").value(3));
  }

  @Test
  void shouldReturnEmptyStatisticsOfPropertyWithoutDefects() throws Exception {
    String empty = api.property("Дом");

    api.send(get(statisticsPath(empty)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalDefects").value(0))
        .andExpect(jsonPath("$.bySeverity[0].label").value("Критический"))
        .andExpect(jsonPath("$.byRoom").isEmpty())
        .andExpect(jsonPath("$.byInspection").isEmpty());
  }

  @Test
  void shouldHidePropertiesOfOtherUsers() throws Exception {
    Fixtures stranger = new Fixtures(mockMvc, ApiUsers.newInspector(mockMvc));

    stranger.send(get(statisticsPath(propertyId))).andExpect(status().isNotFound());
  }

  private String defect(String inspectionId, String roomId, String severity) throws Exception {
    String body = Fixtures.defectBody(roomId, crackId, "Трещина").replace("MAJOR", severity);
    return idOf(api.created("/api/v1/inspections/" + inspectionId + "/defects", body));
  }

  private static String statisticsPath(String propertyId) {
    return "/api/v1/properties/" + propertyId + "/statistics";
  }
}
