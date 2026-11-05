package dev.inspecto.core.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Registers users through the public API and returns their bearer tokens. */
public final class ApiUsers {

  private static final String PASSWORD = "Kitchen-Window-Crack-42";

  private ApiUsers() {}

  /** Registers a new inspector and returns {@code Authorization} header value. */
  public static String newInspector(MockMvc mockMvc) throws Exception {
    String email = "user-" + UUID.randomUUID() + "@example.com";
    String credentials = "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"";
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials + ",\"fullName\":\"Тест\"}"))
        .andExpect(status().isCreated());
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials + ",\"client\":\"android\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return "Bearer " + JsonPath.read(body, "$.accessToken");
  }
}
