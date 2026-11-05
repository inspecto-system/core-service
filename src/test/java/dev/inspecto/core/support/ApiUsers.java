package dev.inspecto.core.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Registers users through the public API and returns their bearer tokens. */
public final class ApiUsers {

  private static final String PASSWORD = "Kitchen-Window-Crack-42";

  private ApiUsers() {}

  /** Registers a new inspector and returns the {@code Authorization} header value. */
  public static String newInspector(MockMvc mockMvc) throws Exception {
    return login(mockMvc, register(mockMvc));
  }

  /**
   * Registers a user and makes it an administrator. The API has no role management yet, so the role
   * is changed directly in the database before signing in.
   */
  public static String newAdmin(MockMvc mockMvc, JdbcTemplate jdbc) throws Exception {
    String email = register(mockMvc);
    jdbc.update("update users set role = 'ADMIN' where email = ?", email);
    return login(mockMvc, email);
  }

  private static String register(MockMvc mockMvc) throws Exception {
    String email = "user-" + UUID.randomUUID() + "@example.com";
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email) + ",\"fullName\":\"Тест\"}"))
        .andExpect(status().isCreated());
    return email;
  }

  private static String login(MockMvc mockMvc, String email) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(email) + ",\"client\":\"android\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return "Bearer " + JsonPath.read(body, "$.accessToken");
  }

  private static String credentials(String email) {
    return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"";
  }
}
