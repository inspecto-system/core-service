package dev.inspecto.core.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Creates properties, rooms, inspections and defects of one user through the public API. */
public final class Fixtures {

  /** System category used by default; created by migration V4. */
  public static final String CRACK = "CRACK";

  private final MockMvc mockMvc;
  private final String bearer;

  /** Fixtures acting as the user with the given {@code Authorization} header value. */
  public Fixtures(MockMvc mockMvc, String bearer) {
    this.mockMvc = mockMvc;
    this.bearer = bearer;
  }

  /** Sends a JSON request as the user. */
  public ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(
        request.header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON));
  }

  /** Creates a resource and returns the response body. */
  public String created(String path, String body) throws Exception {
    return send(post(path).content(body))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /** Response body of a successful GET. */
  public String read(String path) throws Exception {
    return send(get(path))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /** Creates a property and returns its id. */
  public String property(String name) throws Exception {
    return idOf(
        created(
            "/api/v1/properties",
            "{\"name\":\"" + name + "\",\"type\":\"APARTMENT\",\"address\":\"ул. Мира, 5\"}"));
  }

  /** Creates a room and returns its id. */
  public String room(String propertyId, String name, int sortOrder) throws Exception {
    String body =
        "{\"name\":\"" + name + "\",\"roomType\":\"OTHER\",\"sortOrder\":" + sortOrder + "}";
    return idOf(created("/api/v1/properties/" + propertyId + "/rooms", body));
  }

  /** Creates a draft inspection with the standard checklist and returns the response body. */
  public String inspection(String propertyId, String inspectedAt, List<String> roomIds)
      throws Exception {
    String rooms = roomIds.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
    String body =
        "{\"inspectedAt\":\""
            + inspectedAt
            + "\",\"roomIds\":["
            + rooms
            + "],\"templateCodes\":[\"ROOM_STANDARD\"]}";
    return created("/api/v1/properties/" + propertyId + "/inspections", body);
  }

  /** Creates and starts an inspection and returns its id. */
  public String startedInspection(String propertyId, String inspectedAt, List<String> roomIds)
      throws Exception {
    String id = idOf(inspection(propertyId, inspectedAt, roomIds));
    send(patch("/api/v1/inspections/" + id).content("{\"status\":\"IN_PROGRESS\",\"version\":0}"))
        .andExpect(status().isOk());
    return id;
  }

  /** Id of a system category by its code. */
  public String systemCategory(String code) throws Exception {
    List<String> ids =
        JsonPath.read(read("/api/v1/defect-categories"), "$[?(@.code == '" + code + "')].id");
    return ids.getFirst();
  }

  /** Creates an own category and returns its id. */
  public String ownCategory(String name) throws Exception {
    return idOf(created("/api/v1/defect-categories", "{\"name\":\"" + name + "\"}"));
  }

  /** Registers a defect in the inspection and returns the response body. */
  public String defect(String inspectionId, String roomId, String categoryId, String title)
      throws Exception {
    return created(
        "/api/v1/inspections/" + inspectionId + "/defects", defectBody(roomId, categoryId, title));
  }

  /** Request body of a new {@code MAJOR} wall defect. */
  public static String defectBody(String roomId, String categoryId, String title) {
    return "{\"roomId\":\""
        + roomId
        + "\",\"categoryId\":\""
        + categoryId
        + "\",\"elementGroup\":\"WALLS\",\"title\":\""
        + title
        + "\",\"severity\":\"MAJOR\"}";
  }

  /** Records an observation of the defect in the inspection and returns the response body. */
  public String observation(String inspectionId, String defectId, String status) throws Exception {
    return created(
        "/api/v1/inspections/" + inspectionId + "/observations",
        "{\"defectId\":\"" + defectId + "\",\"status\":\"" + status + "\"}");
  }

  /** Value of {@code $.id}. */
  public static String idOf(String json) {
    return JsonPath.read(json, "$.id");
  }
}
