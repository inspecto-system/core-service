package dev.inspecto.core.user;

import static dev.inspecto.core.support.Fixtures.idOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.inspecto.core.TestcontainersConfiguration;
import dev.inspecto.core.support.ApiUsers;
import dev.inspecto.core.support.Fixtures;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Deletion of an account with all its data (T-303, ADR-018). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AccountDeletionIntegrationTest {

  /** Tables with rows of a user, and the column that leads to the user. */
  private static final Map<String, String> OWNED_ROWS =
      Map.of(
          "users", "id = ?::uuid",
          "refresh_tokens", "user_id = ?::uuid",
          "properties", "owner_id = ?::uuid",
          "defect_categories", "owner_id = ?::uuid",
          "inspections", "inspector_id = ?::uuid",
          "defects", "created_by = ?::uuid",
          "defect_observations", "observed_by = ?::uuid",
          "defect_status_history", "changed_by = ?::uuid");

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;

  private Fixtures api;
  private String userId;

  @BeforeEach
  void createAccountWithData() throws Exception {
    api = new Fixtures(mockMvc, ApiUsers.newInspector(mockMvc));
    userId = idOf(api.read("/api/v1/users/me"));
    String propertyId = api.property("Квартира");
    String kitchenId = api.room(propertyId, "Кухня", 0);
    String category = api.ownCategory("Царапины на ламинате");
    String inspectionId =
        api.startedInspection(propertyId, "2026-10-01T09:00:00Z", List.of(kitchenId));
    api.defect(inspectionId, kitchenId, category, "Царапина");
  }

  @Test
  void shouldKeepEverythingWhenPasswordIsWrong() throws Exception {
    api.send(delete("/api/v1/users/me").content("{\"password\":\"Wrong-Password-1\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("password"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID"));
    api.send(delete("/api/v1/users/me").content("{}")).andExpect(status().isBadRequest());

    assertThat(rowsOf(userId)).allSatisfy((table, count) -> assertThat(count).isPositive());
    assertThat(jobs()).isZero();
  }

  @Test
  void shouldDeleteAllRowsAndScheduleFileCleanUp() throws Exception {
    Fixtures other = new Fixtures(mockMvc, ApiUsers.newInspector(mockMvc));
    String otherId = idOf(other.read("/api/v1/users/me"));
    other.property("Дом соседа");

    api.send(delete("/api/v1/users/me").content(password())).andExpect(status().isAccepted());

    assertThat(rowsOf(userId)).allSatisfy((table, count) -> assertThat(count).as(table).isZero());
    assertThat(jobs()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select payload->>'ownerId' from outbox_events where type = 'DELETE_USER_FILES'",
                String.class))
        .isEqualTo(userId);
    // The access token lives on for minutes, but the account is gone.
    api.send(get("/api/v1/users/me")).andExpect(status().isNotFound());
    assertThat(rowsOf(otherId).get("properties")).isEqualTo(1);
  }

  @SuppressFBWarnings(
      value = "SECSQLISPRJDBC",
      justification = "Table and column names come from the constant OWNED_ROWS of this test")
  private Map<String, Integer> rowsOf(String id) {
    return OWNED_ROWS.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                e ->
                    jdbc.queryForObject(
                        "select count(*) from " + e.getKey() + " where " + e.getValue(),
                        Integer.class,
                        id)));
  }

  private int jobs() {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from outbox_events where type = 'DELETE_USER_FILES'"
                + " and payload->>'ownerId' = ?",
            Integer.class,
            userId);
    return count == null ? 0 : count;
  }

  private static String password() {
    return "{\"password\":\"" + ApiUsers.PASSWORD + "\"}";
  }
}
