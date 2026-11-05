package dev.inspecto.core.dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
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
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DictionaryApiIntegrationTest {

  private static final String CATEGORIES = "/api/v1/defect-categories";
  private static final String ADMIN_CATEGORIES = "/api/v1/admin/defect-categories";
  private static final String ENGLISH = "en-US,en;q=0.9,ru;q=0.5";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;

  private String user;

  @BeforeEach
  void signIn() throws Exception {
    user = ApiUsers.newInspector(mockMvc);
  }

  @Test
  void shouldListSeveritiesFromLeastToMostSevere() throws Exception {
    mockMvc
        .perform(get("/api/v1/dictionaries/severities").header("Authorization", user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].code", contains("COSMETIC", "MINOR", "MAJOR", "CRITICAL")))
        .andExpect(jsonPath("$[*].rank", contains(1, 2, 3, 4)))
        .andExpect(jsonPath("$[3].name").value("Критический"));
    mockMvc
        .perform(
            get("/api/v1/dictionaries/severities")
                .header("Authorization", user)
                .header("Accept-Language", ENGLISH))
        .andExpect(jsonPath("$[3].name").value("Critical"));
  }

  @Test
  void shouldListSystemChecklistTemplateWithOrderedItems() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/checklist-templates")
                .header("Authorization", user)
                .header("Accept-Language", ENGLISH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("ROOM_STANDARD"))
        .andExpect(jsonPath("$[0].name").value("Standard room acceptance"))
        .andExpect(
            jsonPath(
                "$[0].items[*].elementGroup",
                contains(
                    "WALLS",
                    "CEILING",
                    "FLOOR",
                    "WINDOWS",
                    "DOORS",
                    "PLUMBING",
                    "ELECTRICAL",
                    "OTHER")))
        .andExpect(jsonPath("$[0].items[0].title").value("Walls: evenness, cracks, finish"));
  }

  @Test
  void shouldListSeededSystemCategoriesInRequestedLocale() throws Exception {
    mockMvc
        .perform(get(CATEGORIES).header("Authorization", user).header("Accept-Language", ENGLISH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].code", hasItems("CRACK", "PEELING", "STAIN", "MOLD", "SPALLING")))
        .andExpect(jsonPath("$[?(@.code == 'CRACK')].name", contains("Crack")))
        .andExpect(jsonPath("$[?(@.code == 'CRACK')].names.ru", contains("Трещина")))
        .andExpect(jsonPath("$[?(@.code == 'WINDOW')].elementGroup", contains("WINDOWS")))
        .andExpect(jsonPath("$[*].system", not(hasItem(false))));
  }

  @Test
  void shouldKeepOwnCategoriesPrivateAndAfterSystemOnes() throws Exception {
    String id =
        createOwn(user, "{\"name\":\"  Сколы на подоконнике \",\"elementGroup\":\"WINDOWS\"}");

    List<Boolean> system =
        JsonPath.read(body(get(CATEGORIES).header("Authorization", user)), "$[*].system");
    assertThat(system.get(system.size() - 1)).isFalse();
    assertThat(system.indexOf(false)).isEqualTo(system.size() - 1);
    mockMvc
        .perform(get(CATEGORIES).header("Authorization", user))
        .andExpect(jsonPath("$[?(@.id == '" + id + "')].name", contains("Сколы на подоконнике")))
        .andExpect(jsonPath("$[?(@.id == '" + id + "')].code", contains(nullValue())));

    String other = ApiUsers.newInspector(mockMvc);
    mockMvc
        .perform(get(CATEGORIES).header("Authorization", other))
        .andExpect(jsonPath("$[*].id", not(hasItem(id))));
    patchJson(other, CATEGORIES + "/" + id, "{\"name\":\"Чужое\"}")
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete(CATEGORIES + "/" + id).header("Authorization", other))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldRenameDeactivateAndDeleteOwnCategory() throws Exception {
    String id = createOwn(user, "{\"name\":\"Царапина\"}");

    patchJson(user, CATEGORIES + "/" + id, "{\"name\":\"Глубокая царапина\",\"active\":false}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Глубокая царапина"))
        .andExpect(jsonPath("$.active").value(false));
    mockMvc
        .perform(get(CATEGORIES).header("Authorization", user))
        .andExpect(jsonPath("$[*].id", not(hasItem(id))));
    mockMvc
        .perform(get(CATEGORIES).param("includeInactive", "true").header("Authorization", user))
        .andExpect(jsonPath("$[*].id", hasItem(id)));

    mockMvc
        .perform(delete(CATEGORIES + "/" + id).header("Authorization", user))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get(CATEGORIES).param("includeInactive", "true").header("Authorization", user))
        .andExpect(jsonPath("$[*].id", not(hasItem(id))));
  }

  @Test
  void shouldNotChangeSystemCategoryThroughOwnEndpoints() throws Exception {
    String crack = systemCategoryId("CRACK");

    patchJson(user, CATEGORIES + "/" + crack, "{\"active\":false}")
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete(CATEGORIES + "/" + crack).header("Authorization", user))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectBlankOwnCategoryName() throws Exception {
    postJson(user, CATEGORIES, "{\"name\":\"  \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  void shouldLetAdminManageSystemCategories() throws Exception {
    String admin = ApiUsers.newAdmin(mockMvc, jdbc);
    String code = uniqueCode();

    String created =
        postJson(
                admin,
                ADMIN_CATEGORIES,
                "{\"code\":\""
                    + code
                    + "\",\"names\":{\"ru\":\" Вздутие \",\"en\":\" \"},\"elementGroup\":\"FLOOR\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.system").value(true))
            .andExpect(jsonPath("$.names.ru").value("Вздутие"))
            .andExpect(jsonPath("$.names.en").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(created, "$.id");

    mockMvc
        .perform(get(CATEGORIES).header("Authorization", user).header("Accept-Language", ENGLISH))
        .andExpect(jsonPath("$[?(@.id == '" + id + "')].name", contains("Вздутие")));

    patchJson(
            admin, ADMIN_CATEGORIES + "/" + id, "{\"names\":{\"ru\":\"Вздутие\",\"en\":\"Bulge\"}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.names.en").value("Bulge"))
        .andExpect(jsonPath("$.elementGroup").value("FLOOR"));
    patchJson(admin, ADMIN_CATEGORIES + "/" + id, "{\"active\":false}")
        .andExpect(jsonPath("$.active").value(false));
    mockMvc
        .perform(get(CATEGORIES).header("Authorization", user))
        .andExpect(jsonPath("$[*].id", not(hasItem(id))));
  }

  @Test
  void shouldRejectTakenAndMalformedSystemCodes() throws Exception {
    String admin = ApiUsers.newAdmin(mockMvc, jdbc);

    postJson(admin, ADMIN_CATEGORIES, "{\"code\":\"CRACK\",\"names\":{\"ru\":\"Дубль\"}}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CATEGORY_CODE_TAKEN"));
    postJson(admin, ADMIN_CATEGORIES, "{\"code\":\"crack\",\"names\":{\"ru\":\"x\"}}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("code"));
    postJson(
            admin, ADMIN_CATEGORIES, "{\"code\":\"" + uniqueCode() + "\",\"names\":{\"en\":\"x\"}}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("names.ru"));
  }

  @Test
  void shouldNotLetAdminEditOwnCategoriesAsSystemOnes() throws Exception {
    String admin = ApiUsers.newAdmin(mockMvc, jdbc);
    String own = createOwn(user, "{\"name\":\"Моя\"}");

    patchJson(admin, ADMIN_CATEGORIES + "/" + own, "{\"active\":false}")
        .andExpect(status().isNotFound());
    patchJson(admin, ADMIN_CATEGORIES + "/" + UUID.randomUUID(), "{\"active\":false}")
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldForbidSystemCategoryChangesToInspectors() throws Exception {
    postJson(user, ADMIN_CATEGORIES, "{\"code\":\"" + uniqueCode() + "\",\"names\":{\"ru\":\"x\"}}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    patchJson(user, ADMIN_CATEGORIES + "/" + systemCategoryId("CRACK"), "{\"active\":false}")
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldRequireAuthentication() throws Exception {
    for (String path :
        List.of(CATEGORIES, "/api/v1/checklist-templates", "/api/v1/dictionaries/severities")) {
      mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
    mockMvc
        .perform(
            post(CATEGORIES).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post(ADMIN_CATEGORIES)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"ABC\",\"names\":{\"ru\":\"x\"}}"))
        .andExpect(status().isUnauthorized());
  }

  private String createOwn(String bearer, String body) throws Exception {
    String json =
        postJson(bearer, CATEGORIES, body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.system").value(false))
            .andExpect(jsonPath("$.active").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(json, "$.id");
  }

  private String systemCategoryId(String code) throws Exception {
    List<String> ids =
        JsonPath.read(
            body(get(CATEGORIES).header("Authorization", user)),
            "$[?(@.code == '" + code + "')].id");
    return ids.get(0);
  }

  private String body(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
      throws Exception {
    return mockMvc.perform(request).andReturn().getResponse().getContentAsString();
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

  private static String uniqueCode() {
    return "TEST_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
  }
}
