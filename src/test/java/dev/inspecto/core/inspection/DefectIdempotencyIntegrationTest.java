package dev.inspecto.core.inspection;

import static dev.inspecto.core.support.Fixtures.idOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.inspecto.core.TestcontainersConfiguration;
import dev.inspecto.core.support.ApiUsers;
import dev.inspecto.core.support.Fixtures;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** {@code Idempotency-Key} of a new defect: retries of the Android offline queue (T-308). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DefectIdempotencyIntegrationTest {

  private static final String OCTOBER = "2026-10-01T09:00:00Z";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;

  private Fixtures api;
  private String propertyId;
  private String kitchenId;
  private String crackId;
  private String inspectionId;

  @BeforeEach
  void createInspection() throws Exception {
    api = new Fixtures(mockMvc, ApiUsers.newInspector(mockMvc));
    propertyId = api.property("Квартира");
    kitchenId = api.room(propertyId, "Кухня", 0);
    crackId = api.systemCategory(Fixtures.CRACK);
    inspectionId = api.startedInspection(propertyId, OCTOBER, List.of(kitchenId));
  }

  @Test
  void shouldReturnFirstDefectForRepeatedKey() throws Exception {
    String key = UUID.randomUUID().toString();
    String first = idOf(body(create(api, key, "Трещина").andExpect(status().isCreated())));

    create(api, key, "Трещина")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(first))
        .andExpect(jsonPath("$.observations.length()").value(1));

    assertThat(defectsOfProperty()).isEqualTo(1);
  }

  @Test
  void shouldCreateSeparateDefectsWithoutKeyOrWithAnotherKey() throws Exception {
    create(api, null, "Трещина").andExpect(status().isCreated());
    create(api, null, "Трещина").andExpect(status().isCreated());
    create(api, UUID.randomUUID().toString(), "Трещина").andExpect(status().isCreated());

    assertThat(defectsOfProperty()).isEqualTo(3);
  }

  @Test
  void shouldKeepKeysOfUsersApart() throws Exception {
    String key = "same-key-of-two-users";
    String mine = idOf(body(create(api, key, "Трещина")));
    Fixtures other = new Fixtures(mockMvc, ApiUsers.newInspector(mockMvc));
    String otherProperty = other.property("Дом");
    String otherRoom = other.room(otherProperty, "Кухня", 0);
    String otherInspection = other.startedInspection(otherProperty, OCTOBER, List.of(otherRoom));

    String theirs =
        idOf(
            body(
                other
                    .send(
                        post("/api/v1/inspections/" + otherInspection + "/defects")
                            .header(DefectCreation.HEADER, key)
                            .content(Fixtures.defectBody(otherRoom, crackId, "Трещина")))
                    .andExpect(status().isCreated())));

    assertThat(theirs).isNotEqualTo(mine);
  }

  @Test
  void shouldRejectKeyOfWrongLength() throws Exception {
    create(api, "short", "Трещина")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value(DefectCreation.HEADER))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_LENGTH"));
    create(api, "k".repeat(65), "Трещина").andExpect(status().isBadRequest());

    assertThat(defectsOfProperty()).isZero();
  }

  @Test
  void shouldCreateOneDefectForConcurrentRepetitions()
      throws InterruptedException, ExecutionException {
    String key = UUID.randomUUID().toString();
    try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
      List<Callable<String>> requests = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        requests.add(() -> idOf(body(create(api, key, "Трещина").andExpect(status().isCreated()))));
      }
      List<String> ids = new ArrayList<>();
      for (Future<String> result : pool.invokeAll(requests)) {
        ids.add(result.get());
      }
      assertThat(ids).containsOnly(ids.getFirst());
    }
    assertThat(defectsOfProperty()).isEqualTo(1);
  }

  private ResultActions create(Fixtures user, String key, String title) throws Exception {
    MockHttpServletRequestBuilder request =
        post("/api/v1/inspections/" + inspectionId + "/defects")
            .content(Fixtures.defectBody(kitchenId, crackId, title));
    return user.send(key == null ? request : request.header(DefectCreation.HEADER, key));
  }

  private static String body(ResultActions result) throws UnsupportedEncodingException {
    return result.andReturn().getResponse().getContentAsString();
  }

  private int defectsOfProperty() {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from defects where property_id = ?::uuid", Integer.class, propertyId);
    return count == null ? 0 : count;
  }
}
